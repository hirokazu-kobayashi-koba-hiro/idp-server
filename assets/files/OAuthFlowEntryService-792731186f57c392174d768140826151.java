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
import java.util.Objects;
import org.idp.server.core.openid.authentication.*;
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
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPayload;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.*;
import org.idp.server.core.openid.oauth.exception.OAuthAuthorizeBadRequestException;
import org.idp.server.core.openid.oauth.io.*;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.extension.OAuthDenyReason;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OAuthFlowEntryService implements OAuthFlowApi {

  OAuthProtocols oAuthProtocols;
  OAuthSessionDelegate oAuthSessionDelegate;
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

  public OAuthFlowEntryService(
      OAuthProtocols oAuthProtocols,
      OAuthSessionDelegate oAuthSessiondelegate,
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
      UserLifecycleEventPublisher userLifecycleEventPublisher) {
    this.oAuthProtocols = oAuthProtocols;
    this.oAuthSessionDelegate = oAuthSessiondelegate;
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

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    OAuthRequestResponse requestResponse = oAuthProtocol.request(oAuthRequest);

    if (requestResponse.isOK()) {
      AuthenticationPolicyConfiguration authenticationPolicyConfiguration =
          authenticationPolicyConfigurationQueryRepository.find(
              tenant, StandardAuthFlow.OAUTH.toAuthFlow());
      AuthenticationTransaction authenticationTransaction =
          OAuthAuthenticationTransactionCreator.create(
              tenant, requestResponse, authenticationPolicyConfiguration);
      authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);
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
    AuthenticationPolicy authenticationPolicy = authenticationTransaction.authenticationPolicy();
    Map<String, Object> additionalViewData = new HashMap<>();
    additionalViewData.put("authentication_policy", authenticationPolicy.toMap());

    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(
            tenant, authorizationRequestIdentifier.value(), additionalViewData);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    return oAuthProtocol.getViewData(oAuthViewDataRequest);
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

    OAuthSession oAuthSession =
        oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(authorizationRequestIdentifier.value());
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

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

    if (result.isSuccess()) {
      OAuthSession updated =
          oAuthSession.didAuthentication(result.user(), updatedTransaction.authentication());
      oAuthSessionDelegate.updateSession(updated);
    }

    if (updatedTransaction.isLocked()) {
      UserLifecycleEvent userLifecycleEvent =
          new UserLifecycleEvent(tenant, updatedTransaction.user(), UserLifecycleType.LOCK);
      userLifecycleEventPublisher.publish(userLifecycleEvent);
    }

    eventPublisher.publish(
        tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

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

    FederationInteractor federationInteractor = federationInteractors.get(federationType);

    FederationRequestResponse response =
        federationInteractor.request(
            tenant, authorizationRequestIdentifier, federationType, ssoProvider);

    OAuthSession oAuthSession =
        oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        oAuthSession.user(),
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

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

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

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(oAuthAuthorizeRequest);

    if (authorize.isOk()) {
      userRegistrator.registerOrUpdate(tenant, user);

      String invitationId =
          authorizationRequest.customParams().getValueAsStringOrEmpty("invitation_id");
      if (invitationId != null && !invitationId.isEmpty()) {
        Map<String, Object> payload = Map.of("invitation_id", invitationId, "status", "accepted");
        UserLifecycleEvent event =
            new UserLifecycleEvent(
                tenant,
                user,
                UserLifecycleType.INVITE_COMPLETE,
                new UserLifecycleEventPayload(payload));
        userLifecycleEventPublisher.publish(event);
      }

      authenticationTransactionCommandRepository.delete(
          tenant, authenticationTransaction.identifier());

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
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);
    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    if (Objects.isNull(session)
        || session.isExpire(SystemDateTime.now())
        || Objects.isNull(session.user())) {
      throw new OAuthAuthorizeBadRequestException("invalid_request", "session expired");
    }

    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            session.user(),
            session.authentication());

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(authAuthorizeRequest);

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        session.user(),
        DefaultSecurityEventType.oauth_authorize_with_session.toEventType(),
        requestAttributes);

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

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);
    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(
            tenant, authorizationRequestIdentifier.value(), OAuthDenyReason.access_denied);

    OAuthDenyResponse denyResponse = oAuthProtocol.deny(denyRequest);

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        session.user(),
        DefaultSecurityEventType.oauth_deny.toEventType(),
        requestAttributes);

    authenticationTransactionCommandRepository.delete(
        tenant, authenticationTransaction.identifier());

    return denyResponse;
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(tenant, params);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());

    return oAuthProtocol.logout(oAuthLogoutRequest);
  }
}
