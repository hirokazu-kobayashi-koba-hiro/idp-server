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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.core.openid.authentication.AuthSessionId;
import org.idp.server.core.openid.authentication.AuthSessionValidator;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.AuthenticationInteractor;
import org.idp.server.core.openid.authentication.AuthenticationInteractors;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionAttributes;
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
import org.idp.server.core.openid.oauth.exception.OAuthRequestNotFoundException;
import org.idp.server.core.openid.oauth.io.*;
import org.idp.server.core.openid.oauth.io.OAuthAuthenticationStatusResponse;
import org.idp.server.core.openid.oauth.io.OAuthAuthenticationStatusStatus;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.response.RedirectUriDecidable;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.extension.OAuthDenyReason;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.session.AuthSessionCookieDelegate;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.core.openid.session.OIDCSessionHandler;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.SessionCookieDelegate;
import org.idp.server.core.openid.session.SessionValidationResult;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

/**
 * OAuthFlowEntryService
 *
 * <p>Orchestrates OAuth/OIDC authorization flows with OIDC Session Management. Uses OPSession and
 * ClientSession for session management, similar to Keycloak's UserSession/ClientSession pattern.
 */
@Transaction
public class OAuthFlowEntryService
    implements OAuthFlowApi, OAuthUserDelegate, RedirectUriDecidable {

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
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AesCipher aesCipher;

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
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AesCipher aesCipher) {
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
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.aesCipher = aesCipher;
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

      // This is a top level navigation, so the OP session cookie is readable here even where the
      // authorization view is on another site. Everything the view calls next is third-party and
      // will not see it, so the session is carried on the transaction instead — that is what lets
      // the view offer, and complete, sign-in with the existing session.
      if (opSessionOpt.isPresent()
          && crossSiteAuthorizationView(tenant, requestResponse.authorizationRequest())) {
        authenticationTransaction =
            authenticationTransaction.withAttributes(
                attributesOf(authenticationTransaction)
                    .withOpSessionId(opSessionOpt.get().id().value()));
      }
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

    // The session the view may offer to continue with
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    OPSession opSession =
        authenticatingSession(
            tenant,
            oAuthProtocol.get(tenant, authorizationRequestIdentifier),
            authenticationTransaction);

    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            opSession,
            authenticationTransaction.user(),
            additionalViewData);

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

    OPSession createdSession = null;
    if (updatedTransaction.isSuccess()) {
      // Existing session for session switch policy handling
      OPSession existingSession =
          authenticatingSession(tenant, authorizationRequest, updatedTransaction);

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
      createdSession = opSession;
    }

    AuthenticationProof proof =
        proofForBrowserStep(tenant, authorizationRequest, authenticationInteractor, result);
    carryCrossSiteBinding(tenant, authorizationRequest, updatedTransaction, createdSession, proof);
    if (proof != null) {
      result.withAuthProof(proof.value());
    }

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
    OPSession createdSession = null;
    if (updatedTransaction.isSuccess()) {
      // Existing session for session switch policy handling
      OPSession existingSession =
          authenticatingSession(tenant, authorizationRequest, updatedTransaction);

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
      createdSession = opSession;
    }

    // Federated sign-in ends in this response, which the browser reads on its way back from the
    // external provider. Issued on the step rather than on the transaction, for the same reason as
    // a local interaction: a device step may still follow, and the proof has to be with the
    // browser before that happens.
    AuthenticationProof proof =
        crossSiteAuthorizationView(tenant, authorizationRequest) && result.hasUser()
            ? AuthenticationProof.authenticated(result.user().sub())
            : null;
    carryCrossSiteBinding(tenant, authorizationRequest, updatedTransaction, createdSession, proof);
    if (proof != null) {
      result.withAuthProof(proof.value());
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
    //
    // A flow with no browser step that proves possession had nothing to earn one with — the
    // authentication happened on a device. It is bound to the browser that started it at /complete
    // instead.
    if (handedOffForCompletion(tenant, authorizationRequest, authenticationTransaction)) {
      return alreadyAuthorized();
    }
    if (crossSiteAuthorizationView(tenant, authorizationRequest)
        // Read from the transaction, not the proof store: a cache that cannot answer must not
        // turn into "no browser step" and waive the proof.
        && authenticationTransaction.browserProvedPossession(authenticationInteractors)) {
      if (!presentsAuthenticationProof(authenticationTransaction, params)) {
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

    // Create ClientSession for OIDC Session Management. Without a session here the client gets no
    // sid and back-channel logout cannot find it.
    OPSession opSession =
        authenticatingSession(tenant, authorizationRequest, authenticationTransaction);
    if (opSession != null) {
      createClientSessionAndSetSid(tenant, opSession, authorizationRequest, oAuthAuthorizeRequest);
    }

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

      if (crossSiteAuthorizationView(tenant, authorizationRequest)) {
        // The transaction is kept until /complete, which needs it to write the session the browser
        // leaves with, and the hand-off is what lets /complete know this is that browser. The code
        // is withheld from this response and travels inside the hand-off instead.
        issueCompletionProof(tenant, authenticationTransaction, user.sub(), authorize);
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
   * first-party here: the session can be written, and the browser binding can be read again.
   *
   * <h3>Two checks, answering different questions</h3>
   *
   * <p>The hand-off says "this is the browser that authenticated"; only that browser was given it.
   * The browser binding says "this is the browser that started the request". Either alone lets one
   * direction of attack through:
   *
   * <ul>
   *   <li>Without the hand-off, an attacker who started a request and handed its URL to a victim
   *       could finish it himself once the victim had authenticated.
   *   <li>Without the binding, an attacker who ran a request through with his own credentials could
   *       send a victim here with the hand-off, and this response would write the attacker's OP
   *       session into the victim's browser.
   * </ul>
   *
   * <p>The binding is checked before the hand-off is consumed. The other way round, a caller with
   * no binding could spend the hand-off and lock out the browser it was issued to.
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

    // Reached again after it already completed — the back button, a reload — once the request
    // is gone. The end-user is shown the error page rather than whatever the lookup would throw.
    AuthorizationRequest authorizationRequest;
    try {
      authorizationRequest = oAuthProtocol.get(tenant, authorizationRequestIdentifier);
    } catch (OAuthRequestNotFoundException e) {
      return OAuthCompleteResponse.errorPage(
          tenant, "invalid_request", "authorization request is not in progress.");
    }

    AuthenticationTransaction authenticationTransaction;
    try {
      authenticationTransaction =
          authenticationTransactionQueryRepository.getForUpdate(
              tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());
    } catch (AuthenticationTransactionNotFoundException e) {
      return OAuthCompleteResponse.errorPage(
          tenant, "invalid_request", "authorization request is not in progress.");
    }

    if (!startedByThisBrowser(authenticationTransaction)) {
      return OAuthCompleteResponse.errorPage(
          tenant, "invalid_request", "this browser did not start the authorization request.");
    }

    // It has to be the second of the two stages. The first is what /authorize itself asks for, and
    // accepting one here would let a caller skip that step and arrive with the wrong one.
    //
    // Spent by deleting the transaction below; a second arrival finds nothing to complete.
    AuthenticationProof proof =
        AuthenticationProof.completionOn(attributesOf(authenticationTransaction), aesCipher);
    if (!proof.completes(authProof)) {
      return OAuthCompleteResponse.errorPage(
          tenant, "invalid_request", "authorization request is not in progress.");
    }

    // The redirect target is taken from the proof, never from the query string, so there is
    // nothing here for a caller to point somewhere else.
    RedirectUri to = new RedirectUri(proof.redirectUri());
    if (!registeredRedirectUri(tenant, authorizationRequest).addressesSameTarget(to)) {
      return OAuthCompleteResponse.errorPage(
          tenant, "invalid_request", "redirect target does not match the authorization request.");
    }

    if (authenticationTransaction.isSuccess()) {
      // The session the authentication already created. Passing it in is what stops a second one
      // being created and the first being left behind with nothing pointing at it.
      OPSession existingSession =
          authenticatingSession(tenant, authorizationRequest, authenticationTransaction);
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

    return OAuthCompleteResponse.redirect(to.value());
  }

  /**
   * The proof this step earns the browser, or null when it earns none.
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
   * <p>A flow with no browser-based authentication step at all therefore issues no proof. There is
   * nothing the browser could be asked for that an attacker could not also produce, so {@code
   * /authorize} does not ask (see {@link AuthenticationTransaction#browserProvedPossession}); the
   * request is bound to the browser that started it at {@code /complete}, as strongly as a
   * same-site deployment binds the same flow.
   */
  private AuthenticationProof proofForBrowserStep(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      AuthenticationInteractor authenticationInteractor,
      AuthenticationInteractionRequestResult result) {

    if (!crossSiteAuthorizationView(tenant, authorizationRequest)) {
      return null;
    }
    if (!authenticationInteractor.isBrowserBased()) {
      return null;
    }
    if (!result.isSuccess() || !result.operationType().provesPossession()) {
      return null;
    }
    if (!result.hasUser()) {
      // A proof that cannot say whose it is would be refused by /authorize anyway. Not issuing one
      // has the same outcome, without reading a sub that is not there.
      return null;
    }
    return AuthenticationProof.authenticated(result.user().sub());
  }

  /**
   * Writes what a cross-site flow carries in place of cookies onto the transaction: the session
   * this step created, and the proof it earned.
   *
   * <p>On the transaction because the caller that created the session is not always the browser:
   * when a device completes the last step, the session is created on its call, and nothing returned
   * there reaches the browser that goes on to {@code /authorize}.
   */
  private void carryCrossSiteBinding(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      AuthenticationTransaction authenticationTransaction,
      OPSession createdSession,
      AuthenticationProof proof) {

    if (!crossSiteAuthorizationView(tenant, authorizationRequest)) {
      return;
    }
    if (createdSession == null && proof == null) {
      return;
    }
    AuthenticationTransactionAttributes attributes = attributesOf(authenticationTransaction);
    if (createdSession != null) {
      attributes = attributes.withOpSessionId(createdSession.id().value());
    }
    if (proof != null) {
      attributes = proof.storeOn(attributes, aesCipher);
    }
    authenticationTransactionCommandRepository.update(
        tenant, authenticationTransaction.withAttributes(attributes));
  }

  /**
   * Whether the caller presented the proof the browser was given for this transaction.
   *
   * <p>The transaction is held under a row lock here, and the proof is spent when {@code
   * /authorize} replaces it with the one for {@code /complete}, so two requests arriving together
   * cannot both pass.
   */
  private boolean presentsAuthenticationProof(
      AuthenticationTransaction authenticationTransaction, Map<String, Object> params) {
    Object value = params != null ? params.get(AuthenticationProof.KEY) : null;
    String presented = value instanceof String string ? string : null;
    return AuthenticationProof.authenticatedOn(attributesOf(authenticationTransaction))
        .authorizes(presented, authenticationTransaction.user().sub());
  }

  /**
   * The OP session this authorization belongs to.
   *
   * <p>Same-site it is the one the cookie points at. Cross-site the cookie never reaches these
   * calls, so it is the one bound to the transaction — the session itself is on the server either
   * way.
   */
  private OPSession authenticatingSession(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      AuthenticationTransaction authenticationTransaction) {
    if (crossSiteAuthorizationView(tenant, authorizationRequest)) {
      AuthenticationTransactionAttributes attributes = attributesOf(authenticationTransaction);
      if (!attributes.hasOpSessionId()) {
        return null;
      }
      return oidcSessionHandler.getOPSession(tenant, attributes.opSessionId()).orElse(null);
    }
    return oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);
  }

  /**
   * Issues the value the same browser must present to {@code /complete}, spending the one for
   * {@code /authorize}.
   *
   * <p>The redirect target travels inside it rather than in the URL, so the completing request has
   * nothing in it for a caller to influence. The code is withheld from this response and travels
   * inside the hand-off instead.
   */
  private void issueCompletionProof(
      Tenant tenant,
      AuthenticationTransaction authenticationTransaction,
      String sub,
      OAuthAuthorizeResponse authorize) {

    // Asked of the response directly. contents() changes with whether an auth proof is set, so
    // reading the redirect from there would depend on the order of the calls.
    String target = authorize.redirectUriValue();
    if (target == null || target.isEmpty()) {
      return;
    }
    AuthenticationTransactionAttributes attributes = attributesOf(authenticationTransaction);
    attributes = AuthenticationProof.authenticatedOn(attributes).spendOn(attributes);
    AuthenticationProof completion = AuthenticationProof.forCompletion(sub, target);
    authenticationTransactionCommandRepository.update(
        tenant,
        authenticationTransaction.withAttributes(completion.storeOn(attributes, aesCipher)));
    authorize.withAuthProof(completion.value());
  }

  /**
   * Whether this authorization has already been authorized and is waiting for {@code /complete}.
   *
   * <p>Cross-site the transaction stays until {@code /complete}, and for flows that ask for no
   * proof at {@code /authorize} — device-only authentication, sign-in with an existing session —
   * anyone holding the request id can call it. Authorizing again would mint another code and
   * replace the hand-off the browser was given, locking that browser out. The first hand-off
   * stands.
   */
  private boolean handedOffForCompletion(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      AuthenticationTransaction authenticationTransaction) {
    return crossSiteAuthorizationView(tenant, authorizationRequest)
        && AuthenticationProof.completionOn(attributesOf(authenticationTransaction), aesCipher)
            .exists();
  }

  private OAuthAuthorizeResponse alreadyAuthorized() {
    return new OAuthAuthorizeResponse(
        OAuthAuthorizeStatus.BAD_REQUEST,
        "invalid_request",
        "authorization request has already been authorized.");
  }

  private AuthenticationTransactionAttributes attributesOf(
      AuthenticationTransaction authenticationTransaction) {
    return authenticationTransaction.hasAttributes()
        ? authenticationTransaction.attributes()
        : new AuthenticationTransactionAttributes();
  }

  /**
   * The redirect URI this request is entitled to.
   *
   * <p>Decided by {@link RedirectUriDecidable}, the same way the authorization response decided it.
   * {@code redirect_uri} is optional for an OAuth 2.0 request whose client registered exactly one,
   * so reading it off the request alone yields null for those — and comparing against null is not a
   * rejection, it is a crash.
   */
  private RedirectUri registeredRedirectUri(
      Tenant tenant, AuthorizationRequest authorizationRequest) {
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, authorizationRequest.requestedClientId());
    return decideRedirectUri(authorizationRequest, clientConfiguration);
  }

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

    // Validate AUTH_SESSION cookie to prevent authorization flow hijacking attacks. Held for
    // update:
    // cross-site, the hand-off for /complete is written back to it.
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.getForUpdate(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());
    validateAuthSession(tenant, authenticationTransaction);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    if (handedOffForCompletion(tenant, authorizationRequest, authenticationTransaction)) {
      return alreadyAuthorized();
    }

    // Same-site from the cookie; cross-site from the request, where the authorization endpoint put
    // it while the cookie was still readable.
    OPSession opSession =
        authenticatingSession(tenant, authorizationRequest, authenticationTransaction);

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
      // Nothing here identifies the caller — the session was bound to the transaction at the
      // authorization request, and this XHR carries no cookie — so the code is handed off, and
      // /complete delivers it only to the browser holding the binding cookie of the request.
      if (crossSiteAuthorizationView(tenant, authorizationRequest)) {
        issueCompletionProof(tenant, authenticationTransaction, user.sub(), authorize);
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
   * Whether this request's authorization view is somewhere this server's cookies cannot reach.
   *
   * <p>The answer selects which of the two browser bindings the flow uses, and the two are
   * exclusive: a cookie the browser never receives cannot also be required. It is declared rather
   * than derived — see {@link
   * org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration#crossSite()}.
   *
   * <p>Asked per client, not per tenant, so that relying parties can be moved over one at a time;
   * see {@link ClientConfiguration#crossSiteAuthorizationView}.
   */
  private boolean crossSiteAuthorizationView(
      Tenant tenant, AuthorizationRequest authorizationRequest) {
    return crossSiteAuthorizationView(tenant, authorizationRequest.requestedClientId());
  }

  private boolean crossSiteAuthorizationView(
      Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    return crossSiteAuthorizationView(
        tenant, authenticationTransaction.request().requestedClientId());
  }

  private boolean crossSiteAuthorizationView(Tenant tenant, RequestedClientId requestedClientId) {
    return clientConfigurationQueryRepository
        .get(tenant, requestedClientId)
        .crossSiteAuthorizationView(tenant.uiConfiguration());
  }

  /**
   * Whether the browser binding cookie matches the one issued when this request started.
   *
   * <p>Asked regardless of topology, unlike {@link #validateAuthSession}: {@code /complete} is a
   * top level navigation, so the cookie reaches it even where the authorization view is on another
   * site. A tenant whose policy opts out of the binding is still honoured.
   */
  private boolean startedByThisBrowser(AuthenticationTransaction authenticationTransaction) {
    AuthSessionId cookieAuthSessionId =
        authSessionCookieDelegate
            .getAuthSessionId()
            .map(AuthSessionId::new)
            .orElse(new AuthSessionId());
    try {
      AuthSessionValidator.validate(authenticationTransaction, cookieAuthSessionId);
      return true;
    } catch (UnauthorizedException e) {
      return false;
    }
  }

  /**
   * Validates AUTH_SESSION cookie against the transaction's authSessionId.
   *
   * <p>Skipped where the authorization view is on another site, because the call carrying this
   * check is then third-party and the cookie is not sent — the check could only ever fail, which is
   * why the binding had to be turned off in the authentication policy ({@code
   * auth_session_binding_required}) to make such a deployment work at all. Those deployments are
   * bound by the hand-off instead, which {@code /authorize} requires in its place, and by the
   * cookie again at {@code /complete}, where it does arrive.
   *
   * @param authenticationTransaction the transaction to validate against
   * @throws org.idp.server.platform.exception.UnauthorizedException if validation fails
   */
  private void validateAuthSession(
      Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    if (crossSiteAuthorizationView(tenant, authenticationTransaction)) {
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
