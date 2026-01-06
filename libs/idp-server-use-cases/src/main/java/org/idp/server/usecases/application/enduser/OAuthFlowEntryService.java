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
import org.idp.server.core.openid.authentication.AuthorizationIdentifier;
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
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.*;
import org.idp.server.core.openid.oauth.io.*;
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
      OIDCSessionHandler oidcSessionHandler) {
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
  }

  @Override
  public OAuthPushedRequestResponse push(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthPushedRequest pushedRequest = new OAuthPushedRequest(tenant, authorizationHeader, params);
    pushedRequest.setClientCert(clientCert);

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
      // Generate AUTH_SESSION for browser session binding (prevents session fixation attacks)
      AuthSessionId authSessionId = AuthSessionId.generate();

      AuthenticationPolicyConfiguration authenticationPolicyConfiguration =
          authenticationPolicyConfigurationQueryRepository.find(
              tenant, StandardAuthFlow.OAUTH.toAuthFlow());
      AuthenticationTransaction authenticationTransaction =
          OAuthAuthenticationTransactionCreator.create(
              tenant, requestResponse, authenticationPolicyConfiguration, authSessionId);
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
    validateAuthSession(authenticationTransaction);

    AuthenticationPolicy authenticationPolicy = authenticationTransaction.authenticationPolicy();
    Map<String, Object> additionalViewData = new HashMap<>();
    additionalViewData.put("authentication_policy", authenticationPolicy.toMap());

    // Get OPSession from cookie for sessionEnabled check
    OPSession opSession =
        oidcSessionHandler.getOPSessionFromCookie(tenant, sessionCookieDelegate).orElse(null);

    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(
            tenant, authorizationRequestIdentifier.value(), opSession, additionalViewData);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    return oAuthProtocol.getViewData(oAuthViewDataRequest, this);
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

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(authorizationRequestIdentifier.value());
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    // Skip validation for device-based interactors (e.g., push notification) as they don't have the
    // cookie
    if (authenticationInteractor.isBrowserBased()) {
      validateAuthSession(authenticationTransaction);
    }

    AuthenticationInteractionRequestResult result =
        authenticationInteractor.interact(
            tenant,
            authenticationTransaction,
            type,
            request,
            requestAttributes,
            userQueryRepository);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

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
              existingSession);
      oidcSessionHandler.registerSessionCookies(tenant, opSession, sessionCookieDelegate);
    }

    if (updatedTransaction.isLocked()) {
      UserLifecycleEvent userLifecycleEvent =
          new UserLifecycleEvent(tenant, updatedTransaction.user(), UserLifecycleType.LOCK);
      userLifecycleEventPublisher.publish(userLifecycleEvent);
    }

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        result.user(),
        result.eventType(),
        result.response(),
        requestAttributes);

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
    validateAuthSession(authenticationTransaction);

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
        authenticationTransactionQueryRepository.get(
            tenant, result.authorizationRequestIdentifier().toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateAuthSession(authenticationTransaction);

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
              existingSession);
      oidcSessionHandler.registerSessionCookies(tenant, opSession, sessionCookieDelegate);
    }

    eventPublisher.publish(
        tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

    return result;
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateAuthSession(authenticationTransaction);

    User user = authenticationTransaction.user();
    List<String> deniedScopes = authenticationTransaction.deniedScopes();
    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            user,
            authenticationTransaction.isSuccess()
                ? authenticationTransaction.authentication()
                : null);
    oAuthAuthorizeRequest.setDeniedScopes(deniedScopes);

    // Create ClientSession for OIDC Session Management
    oidcSessionHandler
        .getOPSessionFromCookie(tenant, sessionCookieDelegate)
        .ifPresent(
            opSession ->
                createClientSessionAndSetSid(
                    tenant, opSession, authorizationRequest, oAuthAuthorizeRequest));

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(oAuthAuthorizeRequest);

    if (authorize.isOk()) {
      userRegistrator.registerOrUpdate(tenant, user);

      authenticationTransactionCommandRepository.delete(
          tenant, authenticationTransaction.identifier());

      // Clear AUTH_SESSION cookie - authorization flow is complete
      authSessionCookieDelegate.clearAuthSessionCookie(tenant);

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

  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Validate AUTH_SESSION cookie to prevent authorization flow hijacking attacks
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());
    validateAuthSession(authenticationTransaction);

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
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    // Validate AUTH_SESSION cookie to prevent session fixation attacks
    validateAuthSession(authenticationTransaction);

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
   * Validates AUTH_SESSION cookie against the transaction's authSessionId.
   *
   * @param authenticationTransaction the transaction to validate against
   * @throws org.idp.server.platform.exception.UnauthorizedException if validation fails
   */
  private void validateAuthSession(AuthenticationTransaction authenticationTransaction) {
    AuthSessionId cookieAuthSessionId =
        authSessionCookieDelegate
            .getAuthSessionId()
            .map(AuthSessionId::new)
            .orElse(new AuthSessionId());

    AuthSessionValidator.validate(authenticationTransaction, cookieAuthSessionId);
  }
}
