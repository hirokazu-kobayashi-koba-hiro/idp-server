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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.idp.server.core.openid.authentication.AuthSessionId;
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
import org.idp.server.core.openid.oauth.CrossSiteAuthorizationBinding;
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

    // Issue #1907: prompt=none must not authorize from the session a request whose policy checks
    // each request on its own.
    AuthenticationPolicyConfiguration authenticationPolicyConfiguration =
        authenticationPolicyConfigurationQueryRepository.find(
            tenant, StandardAuthFlow.OAUTH.toAuthFlow());
    oAuthRequest.setAuthenticationPolicyConfiguration(authenticationPolicyConfiguration);

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
      CrossSiteAuthorizationBinding binding =
          bindingFor(tenant, requestResponse.authorizationRequest());
      if (opSessionOpt.isPresent()) {
        authenticationTransaction =
            binding.bindSession(authenticationTransaction, opSessionOpt.get().id().value());
      }
      // The value the view presents on its calls in place of the browser binding cookie, which
      // does not reach them where the view is on another site. Handed over in the fragment of the
      // view's URL: it reaches only the browser that made this request.
      CrossSiteAuthorizationBinding.ViewBinding viewBinding =
          binding.issueViewBinding(authenticationTransaction);
      if (viewBinding != null) {
        authenticationTransaction = viewBinding.transaction();
        requestResponse.handToViewInFragment(
            CrossSiteAuthorizationBinding.VIEW_BINDING_PARAMETER, viewBinding.value());
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

    CrossSiteAuthorizationBinding binding = bindingFor(tenant, authenticationTransaction);

    // Validate AUTH_SESSION cookie to prevent information disclosure
    validateViewCall(binding, authenticationTransaction, requestAttributes);

    AuthenticationPolicy authenticationPolicy = authenticationTransaction.authenticationPolicy();
    Map<String, Object> additionalViewData = new HashMap<>();
    additionalViewData.put("authentication_policy", authenticationPolicy.toMap());
    additionalViewData.put(
        "authentication_step_hints",
        authenticationInteractors.viewHints(tenant, methodsOf(authenticationPolicy)));

    // The session the view may offer to continue with. None under a policy that verifies each
    // request (Issue #1907): a session cannot satisfy it, so the view goes straight to sign-in.
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    OPSession opSession =
        authenticationPolicy.verifiesEachRequest()
            ? null
            : authenticatingSession(binding, tenant, authenticationTransaction);

    // Only the user of a succeeded authentication: the transaction can hold a user before that —
    // resolved from a login_hint, or by a first factor — that nobody has proven to be.
    User authenticatedUser =
        authenticationTransaction.isSuccess() ? authenticationTransaction.user() : User.notFound();
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            opSession,
            authenticatedUser,
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

    validateViewCall(
        bindingFor(tenant, authenticationTransaction),
        authenticationTransaction,
        requestAttributes);

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
    CrossSiteAuthorizationBinding binding = bindingFor(tenant, authorizationRequest);

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    // Skip validation for device-based interactors (e.g., push notification) as they don't have the
    // cookie
    if (authenticationInteractor.isBrowserBased()) {
      validateViewCall(binding, lockedTransaction, requestAttributes);
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
      OPSession existingSession = authenticatingSession(binding, tenant, updatedTransaction);

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

    AuthenticationProof proof = binding.proofForStep(authenticationInteractor, result);
    storeIfChanged(tenant, binding.carry(updatedTransaction, sessionIdOf(createdSession), proof));
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
    validateViewCall(
        bindingFor(tenant, authorizationRequest), authenticationTransaction, requestAttributes);

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

    CrossSiteAuthorizationBinding binding = bindingFor(tenant, authorizationRequest);

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateViewCall(binding, authenticationTransaction, requestAttributes);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    // Create OPSession for federated authentication
    OPSession createdSession = null;
    if (updatedTransaction.isSuccess()) {
      // Existing session for session switch policy handling
      OPSession existingSession = authenticatingSession(binding, tenant, updatedTransaction);

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
    AuthenticationProof proof = binding.proofForFederation(result.hasUser() ? result.user() : null);
    storeIfChanged(tenant, binding.carry(updatedTransaction, sessionIdOf(createdSession), proof));
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
    CrossSiteAuthorizationBinding binding = bindingFor(tenant, authorizationRequest);
    validateViewCall(binding, authenticationTransaction, requestAttributes);
    switch (binding.gateAuthorize(
        authenticationTransaction,
        authenticationInteractors,
        params != null ? params.get(AuthenticationProof.KEY) : null)) {
      case ALREADY_HANDED_OFF -> {
        return alreadyAuthorized();
      }
      case PROOF_REJECTED -> {
        return new OAuthAuthorizeResponse(
            OAuthAuthorizeStatus.BAD_REQUEST,
            "invalid_request",
            "auth_proof is missing, already used, or was not issued for this authorization request.");
      }
      case CHECK_BROWSER_COOKIE, PROCEED, SIGNED_IN_DURING_FLOW -> {}
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
    OPSession opSession = authenticatingSession(binding, tenant, authenticationTransaction);
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

      if (binding.crossSite()) {
        // The transaction is kept until /complete, which needs it to write the session the browser
        // leaves with, and the hand-off is what lets /complete know this is that browser. The code
        // is withheld from this response and travels inside the hand-off instead.
        handOff(tenant, binding, authenticationTransaction, user, authorize);
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

    CrossSiteAuthorizationBinding binding = bindingFor(tenant, authorizationRequest);
    AuthSessionId cookieAuthSessionId =
        authSessionCookieDelegate
            .getAuthSessionId()
            .map(AuthSessionId::new)
            .orElse(new AuthSessionId());
    // The binding before the proof, the proof before the redirect: see
    // CrossSiteAuthorizationBinding#checkCompletion. The redirect comes out of the proof, never
    // out of the query string. Spent by deleting the transaction below.
    CrossSiteAuthorizationBinding.CompletionCheck check =
        binding.checkCompletion(
            authenticationTransaction,
            cookieAuthSessionId,
            authProof,
            registeredRedirectUri(tenant, authorizationRequest));
    if (!check.isAccepted()) {
      return OAuthCompleteResponse.errorPage(tenant, "invalid_request", check.errorDescription());
    }
    RedirectUri to = check.redirectUri();

    if (authenticationTransaction.isSuccess()) {
      // The session the authentication already created. Passing it in is what stops a second one
      // being created and the first being left behind with nothing pointing at it.
      OPSession existingSession = authenticatingSession(binding, tenant, authenticationTransaction);
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
   * The OP session this authorization belongs to.
   *
   * <p>Same-site it is the one the cookie points at. Cross-site the cookie never reaches these
   * calls, so it is the one bound to the transaction — the session itself is on the server either
   * way.
   */
  private OPSession authenticatingSession(
      CrossSiteAuthorizationBinding binding,
      Tenant tenant,
      AuthenticationTransaction authenticationTransaction) {
    if (binding.crossSite()) {
      String boundSessionId = binding.boundSessionId(authenticationTransaction);
      if (boundSessionId == null) {
        return null;
      }
      return oidcSessionHandler.getOPSession(tenant, boundSessionId).orElse(null);
    }
    return oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);
  }

  private OAuthAuthorizeResponse alreadyAuthorized() {
    return new OAuthAuthorizeResponse(
        OAuthAuthorizeStatus.BAD_REQUEST,
        "invalid_request",
        "authorization request has already been authorized.");
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
    CrossSiteAuthorizationBinding binding = bindingFor(tenant, authenticationTransaction);
    validateViewCall(binding, authenticationTransaction, requestAttributes);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    switch (binding.gateAuthorizeWithSession(authenticationTransaction)) {
      case ALREADY_HANDED_OFF -> {
        return alreadyAuthorized();
      }
      case SIGNED_IN_DURING_FLOW -> {
        return new OAuthAuthorizeResponse(
            OAuthAuthorizeStatus.BAD_REQUEST,
            "invalid_request",
            "a sign-in has already taken place in this authorization request; authorize with the auth_proof it returned.");
      }
      case CHECK_BROWSER_COOKIE, PROCEED, PROOF_REJECTED -> {}
    }

    // Same-site from the cookie; cross-site from the request, where the authorization endpoint put
    // it while the cookie was still readable.
    OPSession opSession = authenticatingSession(binding, tenant, authenticationTransaction);

    SessionValidationResult validationResult =
        oidcSessionHandler.validateSessionForAuthorization(
            opSession,
            authorizationRequest,
            authenticationTransaction.authenticationPolicy(),
            authenticationTransaction.requestForPolicy());

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
      if (binding.crossSite()) {
        handOff(tenant, binding, authenticationTransaction, user, authorize);
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
    validateViewCall(
        bindingFor(tenant, authenticationTransaction),
        authenticationTransaction,
        requestAttributes);

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
   * Issues the hand-off for {@code /complete} and withholds the code from this response: it travels
   * inside the hand-off instead, so {@code /complete} delivers it only to the browser that holds
   * it.
   */
  private void handOff(
      Tenant tenant,
      CrossSiteAuthorizationBinding binding,
      AuthenticationTransaction authenticationTransaction,
      User user,
      OAuthAuthorizeResponse authorize) {
    // Asked of the response directly. contents() changes with whether an auth proof is set, so
    // reading the redirect from there would depend on the order of the calls.
    CrossSiteAuthorizationBinding.HandOff handOff =
        binding.handOff(authenticationTransaction, user, authorize.redirectUriValue());
    if (handOff == null) {
      return;
    }
    authenticationTransactionCommandRepository.update(tenant, handOff.transaction());
    authorize.withAuthProof(handOff.proofValue());
  }

  private void storeIfChanged(Tenant tenant, AuthenticationTransaction changed) {
    if (changed != null) {
      authenticationTransactionCommandRepository.update(tenant, changed);
    }
  }

  /** The methods a policy can ask for: its steps and anything else it makes available. */
  private static Set<String> methodsOf(AuthenticationPolicy authenticationPolicy) {
    Set<String> methods = new HashSet<>(authenticationPolicy.availableMethods());
    authenticationPolicy
        .stepDefinitions()
        .forEach(step -> methods.add(step.authenticationMethod()));
    return methods;
  }

  private static String sessionIdOf(OPSession opSession) {
    return opSession != null ? opSession.id().value() : null;
  }

  private CrossSiteAuthorizationBinding bindingFor(
      Tenant tenant, AuthorizationRequest authorizationRequest) {
    return new CrossSiteAuthorizationBinding(
        crossSiteAuthorizationView(tenant, authorizationRequest.requestedClientId()), aesCipher);
  }

  private CrossSiteAuthorizationBinding bindingFor(
      Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    return new CrossSiteAuthorizationBinding(
        crossSiteAuthorizationView(tenant, authenticationTransaction.request().requestedClientId()),
        aesCipher);
  }

  /**
   * Whether this request's authorization view is somewhere this server's cookies cannot reach.
   *
   * <p>Asked once per request, through {@link #bindingFor}. It is declared rather than derived —
   * see {@link org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration#crossSite()} —
   * and asked per client, so relying parties can be moved over one at a time; see {@link
   * ClientConfiguration#crossSiteAuthorizationView}.
   */
  private boolean crossSiteAuthorizationView(Tenant tenant, RequestedClientId requestedClientId) {
    return clientConfigurationQueryRepository
        .get(tenant, requestedClientId)
        .crossSiteAuthorizationView(tenant.uiConfiguration());
  }

  /**
   * Verifies that a call from the authorization view comes from the browser that started the
   * request. This reads what the call carries, the browser binding cookie and the view's value in a
   * header, and leaves the rule to {@link CrossSiteAuthorizationBinding#verifyViewCall}.
   *
   * @param binding how this request binds to the browser, decided once per request by the caller
   * @param authenticationTransaction the transaction to validate against
   * @param requestAttributes the call, carrying the view's value in a header where cross-site
   * @throws org.idp.server.platform.exception.UnauthorizedException if validation fails
   */
  private void validateViewCall(
      CrossSiteAuthorizationBinding binding,
      AuthenticationTransaction authenticationTransaction,
      RequestAttributes requestAttributes) {
    AuthSessionId cookieAuthSessionId =
        authSessionCookieDelegate.getAuthSessionId().map(AuthSessionId::new).orElse(null);
    String presented =
        requestAttributes != null
            ? requestAttributes.headerValue(CrossSiteAuthorizationBinding.VIEW_BINDING_HEADER)
            : null;
    binding.verifyViewCall(authenticationTransaction, cookieAuthSessionId, presented);
  }
}
