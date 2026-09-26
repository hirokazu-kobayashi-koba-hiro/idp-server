/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.usecases.application.enduser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.idp.server.core.openid.authentication.AuthSessionId;
import org.idp.server.core.openid.authentication.AuthSessionValidator;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationInteractor;
import org.idp.server.core.openid.authentication.AuthenticationInteractors;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationUserStatusGuard;
import org.idp.server.core.openid.authentication.AuthorizationIdentifier;
import org.idp.server.core.openid.authentication.exception.AuthenticationTransactionNotFoundException;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.federation.*;
import org.idp.server.core.openid.federation.io.FederationCallbackRequest;
import org.idp.server.core.openid.federation.io.FederationRequestResponse;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserRegistrationResult;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.core.openid.identity.hint.LoginHintResolver;
import org.idp.server.core.openid.identity.hint.UserHint;
import org.idp.server.core.openid.identity.hint.UserHintRelatedParams;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.*;
import org.idp.server.core.openid.oauth.AuthenticationProof;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.io.*;
import org.idp.server.core.openid.oauth.io.OAuthAuthenticationStatusResponse;
import org.idp.server.core.openid.oauth.io.OAuthAuthenticationStatusStatus;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.extension.OAuthDenyReason;
import org.idp.server.core.openid.session.AuthSessionCookieDelegate;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.core.openid.session.OIDCSessionHandler;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.SessionCookieDelegate;
import org.idp.server.core.openid.session.SessionValidationResult;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.random.RandomStringGenerator;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * OAuthFlowEntryService
 *
 * <p>Orchestrates OAuth/OIDC authorization flows with OIDC Session Management. Uses OPSession and
 * ClientSession for session management, similar to Keycloak's UserSession/ClientSession pattern.
 */
@Transaction
public class OAuthFlowEntryService implements OAuthFlowApi, OAuthUserDelegate {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(OAuthFlowEntryService.class);

  OAuthProtocols oAuthProtocols;
  SessionCookieDelegate sessionCookieDelegate;
  AuthSessionCookieDelegate authSessionCookieDelegate;
  UserQueryRepository userQueryRepository;
  AuthenticationInteractors authenticationInteractors;
  FederationInteractors federationInteractors;
  UserRegistrator userRegistrator;
  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  AuthenticationPolicyConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository;
  OAuthFlowEventPublisher eventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;
  OIDCSessionHandler oidcSessionHandler;
  CacheStore cacheStore;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public OAuthFlowEntryService(
      OAuthProtocols oAuthProtocols,
      SessionCookieDelegate sessionCookieDelegate,
      AuthSessionCookieDelegate authSessionCookieDelegate,
      AuthenticationInteractors authenticationInteractors,
      FederationInteractors federationInteractors,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      OAuthFlowEventPublisher eventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      OIDCSessionHandler oidcSessionHandler,
      CacheStore cacheStore,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.oAuthProtocols = oAuthProtocols;
    this.sessionCookieDelegate = sessionCookieDelegate;
    this.authSessionCookieDelegate = authSessionCookieDelegate;
    this.authenticationInteractors = authenticationInteractors;
    this.federationInteractors = federationInteractors;
    this.userQueryRepository = userQueryRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository, userCommandRepository);
    this.tenantQueryRepository = tenantQueryRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
    this.eventPublisher = eventPublisher;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
    this.oidcSessionHandler = oidcSessionHandler;
    this.cacheStore = cacheStore;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  @Override
  public OAuthPushedRequestResponse push(
      TenantIdentifier tenantIdentifier,
      HttpRequestInputs inputs,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthPushedRequest pushedRequest = new OAuthPushedRequest(tenant, inputs);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    return oAuthProtocol.push(pushedRequest);
  }

  public OAuthRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

    // Get OPSession from cookie for prompt=none handling
    Optional<OPSession> opSessionOpt =
        oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate);
    opSessionOpt.ifPresent(oAuthRequest::setOPSession);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    OAuthRequestResponse requestResponse = oAuthProtocol.request(oAuthRequest);

    if (requestResponse.isRequiredInteraction()) {
      // For PAR-based requests, delete any existing AuthenticationTransaction before creating
      // a new one (delete-insert pattern). PAR reuses the same stored authorization request
      // (and thus the same authorization_id) across multiple visits to the authorization endpoint
      // with the same request_uri. Without cleanup, duplicate records cause
      // SqlTooManyResultsException on subsequent queries.
      if (requestResponse.isPushedRequest()) {
        AuthorizationIdentifier authorizationIdentifier =
            requestResponse.authorizationRequestIdentifier().toAuthorizationIdentifier();
        authenticationTransactionCommandRepository.deleteByAuthorizationIdentifier(
            tenant, authorizationIdentifier);
      }

      // Generate AUTH_SESSION for browser session binding (prevents session fixation attacks)
      AuthSessionId authSessionId = AuthSessionId.generate();

      // Resolve user from login_hint if present
      User resolvedUser = resolveUserFromLoginHint(tenant, requestResponse);

      AuthenticationPolicyConfiguration authenticationPolicyConfiguration =
          authenticationPolicyConfigurationQueryRepository.find(
              tenant, StandardAuthFlow.OAUTH.toAuthFlow());
      AuthenticationTransaction authenticationTransaction =
          OAuthAuthenticationTransactionCreator.create(
              tenant,
              requestResponse,
              authenticationPolicyConfiguration,
              authSessionId,
              resolvedUser);
      authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);

      // Register AUTH_SESSION cookie with same expiry as authorization request
      authSessionCookieDelegate.setAuthSessionCookie(
          tenant, authSessionId.value(), requestResponse.oauthAuthorizationRequestExpiresIn());
    }

    return requestResponse;
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent information disclosure
    validateAuthSession(tenant, authenticationTransaction);

    AuthenticationPolicy authenticationPolicy = authenticationTransaction.authenticationPolicy();
    Map<String, Object> additionalViewData = new HashMap<>();
    additionalViewData.put("authentication_policy", authenticationPolicy.toMap());

    // Get OPSession from cookie for sessionEnabled check
    OPSession opSession =
        oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);

    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            opSession,
            authenticationTransaction.user(),
            additionalViewData);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    return oAuthProtocol.getViewData(oAuthViewDataRequest, this);
  }

  @Override
  public OAuthAuthenticationStatusResponse getAuthenticationStatus(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    validateAuthSession(tenant, authenticationTransaction);

    return new OAuthAuthenticationStatusResponse(
        OAuthAuthenticationStatusStatus.OK,
        authenticationTransaction.authenticationStatus(),
        authenticationTransaction.interactionResultsAsMapObject(),
        authenticationTransaction.interactionResults().authenticationMethods());
  }

  @Override
  public boolean userExists(Tenant tenant, UserIdentifier userIdentifier) {
    User user = userQueryRepository.findById(tenant, userIdentifier);
    return user.exists();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(authorizationRequestIdentifier.value());
    AuthenticationTransaction lockedTransaction =
        authenticationTransactionQueryRepository.getForUpdate(tenant, authorizationIdentifier);

    return interactInternal(tenant, lockedTransaction, type, request, requestAttributes);
  }

  @Override
  public AuthenticationInteractionRequestResult interactInternal(
      Tenant tenant,
      AuthenticationTransaction lockedTransaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    AuthorizationRequestIdentifier authorizationRequestIdentifier =
        new AuthorizationRequestIdentifier(lockedTransaction.authorizationIdentifier().value());

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    // Skip validation for device-based interactors (e.g., push notification) as they don't have the
    // cookie
    if (authenticationInteractor.isBrowserBased()) {
      validateAuthSession(tenant, lockedTransaction);
    }

    // #1377: reject a non-active user before onAuthenticationSuccess() creates an OP session /
    // SSO cookie. /authorize re-checks status and blocks the code, but only after the session
    // already exists.
    AuthenticationInteractionRequestResult result =
        AuthenticationUserStatusGuard.denyIfInactive(
            authenticationInteractor.interact(
                tenant, lockedTransaction, type, request, requestAttributes, userQueryRepository));

    AuthenticationTransaction updatedTransaction = lockedTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        result.user(),
        result.eventType(),
        result.response(),
        requestAttributes);

    if (updatedTransaction.isSuccess()) {
      // Get existing session from cookie for session switch policy handling
      OPSession existingSession =
          oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);

      // Create or reuse OPSession based on session switch policy
      Authentication authentication = updatedTransaction.authentication();
      OPSession opSession =
          oidcSessionHandler.onAuthenticationSuccess(
              tenant,
              updatedTransaction.user(),
              authentication,
              updatedTransaction.interactionResults().toStorageMap(),
              existingSession,
              requestAttributes);
      // This runs on an XHR from the authorization view. Where that view is on another site the
      // request is third-party, and Safari drops the Set-Cookie outright — which is why /complete
      // writes the cookie again from a first-party top level navigation. The write is kept here so
      // that an authorization view which has not been updated to go through /complete still ends
      // up with a session on a same-site deployment; there, /complete finds this cookie and reuses
      // the session rather than creating a second one.
      oidcSessionHandler.registerSessionCookies(tenant, opSession, sessionCookieDelegate);
    }

    issueProofIfBrowserAuthenticated(
        tenant, authorizationRequestIdentifier, authenticationInteractor, result);

    if (updatedTransaction.isLocked() && result.hasUser()) {
      log.warn(
          "Account lock conditions met, publishing LOCK lifecycle event: sub={}, user_name={}",
          result.user().sub(),
          result.user().preferredUsername());
      UserLifecycleEvent userLifecycleEvent =
          new UserLifecycleEvent(tenant, result.user(), UserLifecycleType.LOCK);
      userLifecycleEventPublisher.publish(userLifecycleEvent);

      eventPublisher.publish(
          tenant,
          authorizationRequest,
          result.user(),
          DefaultSecurityEventType.user_lock.toEventType(),
          requestAttributes);
    }

    return result;
  }

  public FederationRequestResponse requestFederation(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateAuthSession(tenant, authenticationTransaction);

    FederationInteractor federationInteractor = federationInteractors.get(federationType);

    FederationRequestResponse response =
        federationInteractor.request(
            tenant, authorizationRequestIdentifier, federationType, ssoProvider);

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        new User(),
        DefaultSecurityEventType.federation_request.toEventType(),
        requestAttributes);

    return response;
  }

  public FederationInteractionResult callbackFederation(
      TenantIdentifier tenantIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider,
      FederationCallbackRequest callbackRequest,
      RequestAttributes requestAttributes) {

    FederationInteractor federationInteractor = federationInteractors.get(federationType);
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationInteractionResult result =
        federationInteractor.callback(
            tenant, federationType, ssoProvider, callbackRequest, userQueryRepository);

    if (result.isError()) {
      return result;
    }

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, result.authorizationRequestIdentifier());

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.getForUpdate(
            tenant, result.authorizationRequestIdentifier().toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateAuthSession(tenant, authenticationTransaction);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    // Create OPSession for federated authentication
    if (updatedTransaction.isSuccess()) {
      // Get existing session from cookie for session switch policy handling
      OPSession existingSession =
          oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);

      Authentication authentication = updatedTransaction.authentication();
      OPSession opSession =
          oidcSessionHandler.onAuthenticationSuccess(
              tenant,
              updatedTransaction.user(),
              authentication,
              updatedTransaction.interactionResults().toStorageMap(),
              existingSession,
              requestAttributes);
      // This runs on an XHR from the authorization view. Where that view is on another site the
      // request is third-party, and Safari drops the Set-Cookie outright — which is why /complete
      // writes the cookie again from a first-party top level navigation. The write is kept here so
      // that an authorization view which has not been updated to go through /complete still ends
      // up with a session on a same-site deployment; there, /complete finds this cookie and reuses
      // the session rather than creating a second one.
      oidcSessionHandler.registerSessionCookies(tenant, opSession, sessionCookieDelegate);
    }

    // Federated sign-in ends in this response, which the browser reads on its way back from the
    // external provider. Issued on the step rather than on the transaction, for the same reason as
    // a local interaction: a device step may still follow, and the proof has to be with the
    // browser before that happens.
    if (crossSiteAuthorizationView(tenant) && !result.isError()) {
      result.withAuthProof(
          issueAuthenticationProof(
              tenant, result.authorizationRequestIdentifier(), result.user().sub()));
    }

    eventPublisher.publish(
        tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

    return result;
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      Map<String, Object> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.getForUpdate(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    // Which browser is calling. The code is minted below and leaves in this very response, so
    // whatever answers that question has to answer it here — not at /complete, which an attacker
    // holding the request id would never need to reach.
    if (crossSiteAuthorizationView(tenant)) {
      if (!holdsAuthenticationProof(
          tenant, authorizationRequestIdentifier, authenticationTransaction.user(), params)) {
        return new OAuthAuthorizeResponse(
            OAuthAuthorizeStatus.BAD_REQUEST,
            "invalid_request",
            "auth_proof is missing, already used, or was not issued for this authorization request.");
      }
    } else {
      validateAuthSession(tenant, authenticationTransaction);
    }

    User user = authenticationTransaction.user();
    // Policy-enforced (level-of-authentication) denied scopes, plus any the end-user declined on
    // the
    // consent screen (authorize request body).
    List<String> deniedScopes = new ArrayList<>(authenticationTransaction.deniedScopes());
    deniedScopes.addAll(extractDeniedScopes(params));
    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            user,
            authenticationTransaction.isSuccess()
                ? authenticationTransaction.authentication()
                : null);
    oAuthAuthorizeRequest.setDeniedScopes(deniedScopes);
    oAuthAuthorizeRequest.setDeniedClaims(extractDeniedClaims(params));
    // Per-element consent for array claims (#1816). Recorded on the grant as the decision and
    // applied when claims are built, so the user below is persisted with everything they own —
    // consent decides what a token carries, never what the user has.
    oAuthAuthorizeRequest.setGrantedClaimValues(params.get("granted_claim_values"));

    // Create ClientSession for OIDC Session Management
    oidcSessionHandler
        .getOPSessionFromCookie(tenant, sessionCookieDelegate)
        .ifPresent(
            opSession ->
                createClientSessionAndSetSid(
                    tenant, opSession, authorizationRequest, oAuthAuthorizeRequest));

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(oAuthAuthorizeRequest);

    if (authorize.isOk()) {
      UserRegistrationResult registrationResult = userRegistrator.registerOrUpdate(tenant, user);

      if (registrationResult.isNewRegistration()) {
        eventPublisher.publish(
            tenant,
            authorizationRequest,
            user,
            DefaultSecurityEventType.user_signup.toEventType(),
            requestAttributes);
      }

      if (crossSiteAuthorizationView(tenant)) {
        // The transaction is kept until /complete, which needs it to write the session the browser
        // leaves with, and the hand-off is what lets /complete know this is that browser. The code
        // is withheld from this response and travels inside the hand-off instead.
        issueCompletionProof(tenant, authorizationRequestIdentifier, user.sub(), authorize);
      } else {
        // Same-origin: the browser goes straight to the client from here, as it always has.
        authenticationTransactionCommandRepository.delete(
            tenant, authenticationTransaction.identifier());
        authSessionCookieDelegate.clearAuthSessionCookie(tenant);
      }

      eventPublisher.publish(
          tenant,
          authorizationRequest,
          user,
          DefaultSecurityEventType.oauth_authorize.toEventType(),
          requestAttributes);
    } else {
      eventPublisher.publish(
          tenant,
          authorizationRequest,
          user,
          DefaultSecurityEventType.authorize_failure.toEventType(),
          requestAttributes);
    }

    return authorize;
  }

  /**
   * Hands the browser back to the client, from a first-party top level navigation.
   *
   * <h3>Why this step exists</h3>
   *
   * <p>Everything between the authorization request and here runs as XHR from the authorization
   * view. When that view is served from another site those calls are third-party, and Safari — with
   * third-party cookies off by default — neither sends the cookies they read nor keeps the ones
   * they set. Two things in the flow depend on cookies, and both were silently lost:
   *
   * <ul>
   *   <li>{@code IDP_AUTH_SESSION}, which binds the authorization request to the browser that
   *       started it. Unreadable on an XHR, so the only way to make the flow work was to turn the
   *       check off — removing the defence against an attacker handing his own authorization URL to
   *       a victim.
   *   <li>{@code IDP_IDENTITY}, the OP session. Unwritable on an XHR, so the browser finished the
   *       flow with no session at all. The client still got its tokens, which is why this looked
   *       like it worked, but anything later needing a browser session — account linking among them
   *       — found none and could never get one.
   * </ul>
   *
   * <p>This endpoint is reached by a top level navigation to this server, so its cookies are
   * first-party here and the session can be written. The browser binding is not read here even so:
   * that cookie belongs to whoever opened the request, which in the attack is the attacker, and the
   * browser that authenticated may never have had one. What is checked is the hand-off, which only
   * the authenticated browser was given.
   *
   * @param authProof the one-time value {@code /authorize} returned to that browser; the redirect
   *     travels inside it, so this URL carries nothing a caller can point elsewhere
   */
  @Transaction
  public OAuthCompleteResponse complete(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      String authProof,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    // Consumed before anything else. This is the whole authorization of this step: the value was
    // issued by /authorize to the browser that had already proved it was the one which
    // authenticated, so holding it is what distinguishes that browser from any other.
    //
    // It has to be the second of the two stages. The first is what /authorize itself asks for, and
    // accepting one here would let a caller skip that step and arrive with the wrong one.
    AuthenticationProof proof = consumeProof(tenant, authProof);
    if (proof == null
        || !proof.hasRedirectUri()
        || !proof.issuedFor(authorizationRequestIdentifier.value())) {
      return OAuthCompleteResponse.error(
          "invalid_request", "authorization request is not in progress.");
    }

    AuthenticationTransaction authenticationTransaction;
    try {
      authenticationTransaction =
          authenticationTransactionQueryRepository.getForUpdate(
              tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());
    } catch (AuthenticationTransactionNotFoundException e) {
      return OAuthCompleteResponse.error(
          "invalid_request", "authorization request is not in progress.");
    }

    // The redirect target is taken from the proof, never from the query string, so there is
    // nothing here for a caller to point somewhere else.
    String to = proof.redirectUri();
    if (!addressesSameTarget(to, registeredRedirectUri(tenant, authorizationRequest))) {
      return OAuthCompleteResponse.error(
          "invalid_request", "redirect target does not match the authorization request.");
    }

    if (authenticationTransaction.isSuccess()) {
      OPSession existingSession =
          oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);
      OPSession opSession =
          oidcSessionHandler.onAuthenticationSuccess(
              tenant,
              authenticationTransaction.user(),
              authenticationTransaction.authentication(),
              authenticationTransaction.interactionResults().toStorageMap(),
              existingSession,
              requestAttributes);
      // First-party write. This is the one that the browser actually keeps.
      oidcSessionHandler.registerSessionCookies(tenant, opSession, sessionCookieDelegate);
    }

    authenticationTransactionCommandRepository.delete(
        tenant, authenticationTransaction.identifier());
    authSessionCookieDelegate.clearAuthSessionCookie(tenant);

    return OAuthCompleteResponse.redirect(to);
  }

  /** How long the browser has to make the hand-off navigation. Seconds, not minutes. */
  private static final int AUTH_PROOF_TTL_SECONDS = 120;

  /** The key the authorize response carries the hand-off under. */
  public static final String AUTH_PROOF_KEY = "auth_proof";

  /**
   * Hands the browser its proof, as soon as it has earned one.
   *
   * <p>Issued on the step itself rather than when the whole transaction succeeds, because the two
   * are often not the same caller. A flow that finishes out of band — a push notification, FIDO UAF
   * — is completed by the device, and a value handed back there never reaches the browser that has
   * to call {@code /authorize}. Whatever browser-based step came first is the last point where the
   * browser is on the line.
   *
   * <p>Two conditions, and both matter. The call has to come from the browser, or the proof goes to
   * the device. And the step has to be one the end-user could only pass by supplying something they
   * hold: sending a code, cancelling, or acknowledging a notification are things anyone with the
   * request id can do, and a proof earned that way would be worth nothing.
   *
   * <p>A flow with no browser-based authentication step at all therefore issues no proof, and
   * {@code /authorize} will refuse it. That combination is not supported cross-site: there is
   * nothing the browser can be asked for that an attacker could not also produce.
   */
  private void issueProofIfBrowserAuthenticated(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthenticationInteractor authenticationInteractor,
      AuthenticationInteractionRequestResult result) {

    if (!crossSiteAuthorizationView(tenant)) {
      return;
    }
    if (!authenticationInteractor.isBrowserBased()) {
      return;
    }
    if (!result.isSuccess() || !result.operationType().provesPossession()) {
      return;
    }

    result.withAuthProof(
        issueAuthenticationProof(tenant, authorizationRequestIdentifier, result.user().sub()));
  }

  /**
   * Issues the value the authenticated browser must present to {@code /authorize}.
   *
   * <p>Handed back in the response to the interaction that succeeded, which only the browser that
   * supplied the credentials receives. Knowing the authorization request id is not enough to get
   * one, which is the whole point: the id is known to whoever opened the request, and in the attack
   * this defends against that is not the person who authenticated.
   */
  private String issueAuthenticationProof(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier, String sub) {

    String authProof = new RandomStringGenerator(32).generate();
    cacheStore.put(
        AuthenticationProof.cacheKey(tenant.identifier().value(), authProof),
        new AuthenticationProof(authorizationRequestIdentifier.value(), sub, null),
        AUTH_PROOF_TTL_SECONDS);
    return authProof;
  }

  /**
   * Whether the caller is the browser that authenticated.
   *
   * <p>The hand-off is consumed here whether or not it turns out to be valid, so a caller cannot
   * try one twice, and a completion hand-off is rejected outright — the two stages are not
   * interchangeable.
   */
  private boolean holdsAuthenticationProof(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      User user,
      Map<String, Object> params) {

    Object value = params != null ? params.get(AUTH_PROOF_KEY) : null;
    if (!(value instanceof String authProof) || authProof.isEmpty()) {
      return false;
    }

    AuthenticationProof consumed = consumeProof(tenant, authProof);
    return consumed != null
        && !consumed.hasRedirectUri()
        && consumed.issuedFor(authorizationRequestIdentifier.value())
        && consumed.issuedTo(user.sub());
  }

  /**
   * Issues the value the same browser must present to {@code /complete}.
   *
   * <p>The redirect target travels inside it rather than in the URL, so the completing request has
   * nothing in it for a caller to influence.
   */
  private void issueCompletionProof(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      String sub,
      OAuthAuthorizeResponse authorize) {

    Object redirectUri = authorize.contents().get("redirect_uri");
    if (!(redirectUri instanceof String target) || target.isEmpty()) {
      return;
    }

    String authProof = new RandomStringGenerator(32).generate();
    cacheStore.put(
        AuthenticationProof.cacheKey(tenant.identifier().value(), authProof),
        new AuthenticationProof(authorizationRequestIdentifier.value(), sub, target),
        AUTH_PROOF_TTL_SECONDS);
    authorize.withAuthProof(authProof);
  }

  /**
   * Claims the proof and removes it, so one authentication finishes one flow.
   *
   * <p>The claim is an atomic increment rather than a read followed by a delete. Two requests
   * arriving together — a double-clicked button is enough — would both read the value before either
   * removed it, and cross-site the transaction is still alive at that point, so both would mint a
   * code. Only the caller that sees the first increment continues.
   *
   * <p>Anything other than a first claim is refused, including the zero a store returns when it
   * failed or is not storing anything. Refusing is the safe direction: the flow stops rather than
   * proceeding unguarded.
   */
  private AuthenticationProof consumeProof(Tenant tenant, String authProof) {
    if (authProof == null || authProof.isEmpty()) {
      return null;
    }
    String key = AuthenticationProof.cacheKey(tenant.identifier().value(), authProof);

    long claim = cacheStore.increment(key + ":claimed", AUTH_PROOF_TTL_SECONDS);
    if (claim != 1) {
      log.warn("auth_proof was already claimed or could not be claimed. claim:{}", claim);
      cacheStore.delete(key);
      return null;
    }

    AuthenticationProof found = cacheStore.find(key, AuthenticationProof.class).orElse(null);
    cacheStore.delete(key);
    return found;
  }

  /**
   * The redirect URI this request is entitled to, decided the same way the authorization response
   * decided it.
   *
   * <p>{@code redirect_uri} is optional for an OAuth 2.0 request whose client registered exactly
   * one, and {@link org.idp.server.core.openid.oauth.response.RedirectUriDecidable} fills it in
   * from the client. Reading it off the request alone yields null for those, and comparing against
   * null is not a rejection — it is a crash.
   */
  private String registeredRedirectUri(Tenant tenant, AuthorizationRequest authorizationRequest) {
    if (authorizationRequest.hasRedirectUri()) {
      return authorizationRequest.redirectUri().value();
    }
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, authorizationRequest.requestedClientId());
    return clientConfiguration.getFirstRedirectUri().value();
  }

  /**
   * Whether {@code to} addresses the same place as the registered redirect URI.
   *
   * <p>Compared structurally rather than by prefix. A prefix test has no boundary, so a client
   * registered with a bare origin — {@code http://localhost:3000}, which plenty are — would accept
   * {@code http://localhost:3000.attacker.example}: it starts with the registered string while
   * pointing somewhere else entirely.
   *
   * <p>Scheme, host, port and path must match. Query and fragment are free, because that is where
   * the authorization response puts the code and state.
   */
  static boolean addressesSameTarget(String to, String registered) {
    if (to == null || to.isEmpty() || registered == null || registered.isEmpty()) {
      return false;
    }
    try {
      URI toUri = new URI(to);
      URI registeredUri = new URI(registered);
      // Userinfo is refused outright rather than compared. It is never part of a registered
      // redirect URI, and it is the part of a URL people misread: https://expected.example@host
      // shows the expected name while addressing host.
      if (toUri.getUserInfo() != null) {
        return false;
      }
      return Objects.equals(toUri.getScheme(), registeredUri.getScheme())
          && Objects.equals(normalizeHost(toUri), normalizeHost(registeredUri))
          && toUri.getPort() == registeredUri.getPort()
          && Objects.equals(normalizePath(toUri), normalizePath(registeredUri));
    } catch (URISyntaxException e) {
      return false;
    }
  }

  /** Host names are case-insensitive, so a differently cased one addresses the same place. */
  private static String normalizeHost(URI uri) {
    String host = uri.getHost();
    return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
  }

  /** An absent path and "/" address the same resource; everything else compares as written. */
  private static String normalizePath(URI uri) {
    String path = uri.getPath();
    return path == null || path.isEmpty() ? "/" : path;
  }

  /**
   * Scope names the end-user declined on the consent screen, taken from the authorize request body
   * ({@code denied_scopes}). Empty when no body / no denial. These are merged with the
   * policy-enforced (level-of-authentication) denied scopes and removed from the granted scopes at
   * grant build time, so the scope and its derived claims are not issued.
   */
  private List<String> extractDeniedScopes(Map<String, Object> params) {
    return extractStringList(params, "denied_scopes");
  }

  /**
   * Claim names the end-user declined to share on the consent screen, taken from the authorize
   * request body ({@code denied_claims}). Empty when no body / no denial. The names are removed
   * from the granted id_token / userinfo / verified_claims at grant build time (OIDC4IDA Section
   * 5.7.3).
   */
  private List<String> extractDeniedClaims(Map<String, Object> params) {
    return extractStringList(params, "denied_claims");
  }

  private List<String> extractStringList(Map<String, Object> params, String key) {
    if (params == null) {
      return List.of();
    }
    Object value = params.get(key);
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Validate AUTH_SESSION cookie to prevent authorization flow hijacking attacks
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());
    validateAuthSession(tenant, authenticationTransaction);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    // Get OPSession from cookie and validate
    OPSession opSession =
        oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);

    SessionValidationResult validationResult =
        oidcSessionHandler.validateSessionForAuthorization(
            opSession, authorizationRequest, authenticationTransaction.authenticationPolicy());

    if (validationResult.isInvalid()) {
      eventPublisher.publish(
          tenant,
          authorizationRequest,
          new User(),
          validationResult.eventType().toEventType(),
          requestAttributes);

      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST,
          validationResult.errorCode(),
          validationResult.errorDescription());
    }

    // Get user from session
    User user = userQueryRepository.findById(tenant, opSession.userIdentifier());
    if (!user.exists()) {
      return new OAuthAuthorizeResponse(
          OAuthAuthorizeStatus.BAD_REQUEST, "invalid_request", "user not found");
    }

    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant, authorizationRequestIdentifier.value(), user, opSession.authentication());

    // Create ClientSession
    createClientSessionAndSetSid(tenant, opSession, authorizationRequest, authAuthorizeRequest);

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(authAuthorizeRequest);

    if (authorize.isOk()) {
      // Update user info on session reuse
      userRegistrator.registerOrUpdate(tenant, user);

      // Same rule as the interactive path: cross-site, the code does not travel in this response.
      // The caller reached this point by presenting an OP session cookie, so it is already the
      // browser the session belongs to and there is no separate proof to ask for — but the
      // redirect still has to be made from a first-party navigation, or the session cookie is
      // never refreshed and the flow leaves nothing behind.
      if (crossSiteAuthorizationView(tenant)) {
        issueCompletionProof(tenant, authorizationRequestIdentifier, user.sub(), authorize);
      }

      eventPublisher.publish(
          tenant,
          authorizationRequest,
          user,
          DefaultSecurityEventType.oauth_authorize_with_session.toEventType(),
          requestAttributes);
    } else {
      eventPublisher.publish(
          tenant,
          authorizationRequest,
          user,
          DefaultSecurityEventType.authorize_failure.toEventType(),
          requestAttributes);
    }

    return authorize;
  }

  public OAuthDenyResponse deny(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.getForUpdate(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateAuthSession(tenant, authenticationTransaction);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    // Get user from AuthenticationTransaction
    User user = authenticationTransaction.user();

    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(
            tenant, authorizationRequestIdentifier.value(), OAuthDenyReason.access_denied);

    OAuthDenyResponse denyResponse = oAuthProtocol.deny(denyRequest);

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        user,
        DefaultSecurityEventType.oauth_deny.toEventType(),
        requestAttributes);

    authenticationTransactionCommandRepository.delete(
        tenant, authenticationTransaction.identifier());

    // Clear AUTH_SESSION cookie - authorization flow is complete (denied)
    authSessionCookieDelegate.clearAuthSessionCookie(tenant);

    return denyResponse;
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(tenant, params);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    OAuthLogoutResponse response = oAuthProtocol.logout(oAuthLogoutRequest);

    if (response.isOk() && response.hasContext()) {
      // Clear session cookies
      sessionCookieDelegate.clearSessionCookies(tenant);

      eventPublisher.publishLogout(
          tenant,
          response.context(),
          DefaultSecurityEventType.logout.toEventType(),
          requestAttributes);
    }

    return response;
  }

  /**
   * Resolves user from login_hint parameter in the authorization request.
   *
   * <p>Reuses CIBA's LoginHintResolver to support the same hint formats (sub:, device:, email:,
   * phone:, ex-sub:). Returns User.notFound() if login_hint is absent or user cannot be found.
   */
  private User resolveUserFromLoginHint(Tenant tenant, OAuthRequestResponse requestResponse) {
    if (!requestResponse.authorizationRequest().hasLoginHint()) {
      return User.notFound();
    }
    String loginHintValue = requestResponse.authorizationRequest().loginHint().value();
    LoginHintResolver resolver = new LoginHintResolver();
    return resolver.resolve(
        tenant, new UserHint(loginHintValue), new UserHintRelatedParams(), userQueryRepository);
  }

  /**
   * Creates a ClientSession and sets the sid in the OAuthAuthorizeRequest.
   *
   * @param tenant the tenant
   * @param opSession the OP session
   * @param authorizationRequest the authorization request
   * @param authorizeRequest the OAuth authorize request to set sid on
   */
  private void createClientSessionAndSetSid(
      Tenant tenant,
      OPSession opSession,
      AuthorizationRequest authorizationRequest,
      OAuthAuthorizeRequest authorizeRequest) {
    ClientSessionIdentifier sid =
        oidcSessionHandler.onAuthorize(
            tenant,
            opSession,
            authorizationRequest.requestedClientId().value(),
            authorizationRequest.scopes().toStringSet(),
            authorizationRequest.nonce().value());
    authorizeRequest.setCustomProperties(Map.of("sid", sid.value()));
  }

  /**
   * Whether this tenant's authorization view is somewhere this server's cookies cannot reach.
   *
   * <p>The answer selects which of the two browser bindings the flow uses, and the two are
   * exclusive: a cookie the browser never receives cannot also be required. It is declared rather
   * than derived — see {@link
   * org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration#crossSite()}.
   */
  private boolean crossSiteAuthorizationView(Tenant tenant) {
    boolean crossSite = tenant.uiConfiguration().crossSite();
    if (crossSite) {
      warnOnceIfProofsCannotBeStored();
    }
    return crossSite;
  }

  private static final AtomicBoolean cacheWarningLogged = new AtomicBoolean(false);

  /**
   * Says so when the proof has nowhere to live.
   *
   * <p>Without a cache the proof cannot be issued, and every authorize call fails with "auth_proof
   * is missing", which says nothing about why. The deployment is misconfigured rather than under
   * attack, and that is worth one line in the log.
   */
  private void warnOnceIfProofsCannotBeStored() {
    if (cacheStore instanceof NoOperationCacheStore
        && cacheWarningLogged.compareAndSet(false, true)) {
      log.warn(
          "ui_config.cross_site is enabled but no cache is configured. auth_proof cannot be stored,"
              + " so authorize will reject every request. Enable the cache or turn cross_site off.");
    }
  }

  /**
   * Validates AUTH_SESSION cookie against the transaction's authSessionId.
   *
   * <p>Skipped where the authorization view is on another site, because the call carrying this
   * check is then third-party and the cookie is not sent — the check could only ever fail, which is
   * why it had to be turned off per tenant to make such a deployment work at all. Those deployments
   * are bound by the hand-off instead, which {@code /authorize} requires in its place.
   *
   * @param authenticationTransaction the transaction to validate against
   * @throws org.idp.server.platform.exception.UnauthorizedException if validation fails
   */
  private void validateAuthSession(
      Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    if (crossSiteAuthorizationView(tenant)) {
      return;
    }

    AuthSessionId cookieAuthSessionId =
        authSessionCookieDelegate
            .getAuthSessionId()
            .map(AuthSessionId::new)
            .orElse(new AuthSessionId());

    AuthSessionValidator.validate(authenticationTransaction, cookieAuthSessionId);
  }
}
