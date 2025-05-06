package org.idp.server.usecases.application;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.type.extension.OAuthDenyReason;
import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.federation.*;
import org.idp.server.core.federation.io.FederationCallbackRequest;
import org.idp.server.core.federation.io.FederationRequestResponse;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRegistrator;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.oidc.*;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.io.*;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.security.event.OAuthFlowEventPublisher;

@Transaction
public class OAuthFlowEntryService implements OAuthFlowApi {

  OAuthProtocols oAuthProtocols;
  OAuthSessionDelegate oAuthSessionDelegate;
  UserQueryRepository userQueryRepository;
  AuthenticationInteractors authenticationInteractors;
  FederationInteractors federationInteractors;
  UserRegistrator userRegistrator;
  TenantRepository tenantRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository;
  OAuthFlowEventPublisher eventPublisher;

  public OAuthFlowEntryService(
      OAuthProtocols oAuthProtocols,
      OAuthSessionDelegate oAuthSessiondelegate,
      AuthenticationInteractors authenticationInteractors,
      FederationInteractors federationInteractors,
      UserQueryRepository userQueryRepository,
      TenantRepository tenantRepository,
      AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository,
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      OAuthFlowEventPublisher eventPublisher) {
    this.oAuthProtocols = oAuthProtocols;
    this.oAuthSessionDelegate = oAuthSessiondelegate;
    this.authenticationInteractors = authenticationInteractors;
    this.federationInteractors = federationInteractors;
    this.userQueryRepository = userQueryRepository;
    this.userRegistrator = new UserRegistrator(userQueryRepository);
    this.tenantRepository = tenantRepository;
    this.authenticationTransactionCommandRepository = authenticationTransactionCommandRepository;
    this.authenticationTransactionQueryRepository = authenticationTransactionQueryRepository;
    this.eventPublisher = eventPublisher;
  }

  public Pairs<Tenant, OAuthRequestResponse> request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
    OAuthRequestResponse requestResponse = oAuthProtocol.request(oAuthRequest);

    if (requestResponse.isOK()) {
      AuthenticationTransaction authenticationTransaction =
          AuthenticationTransaction.createOnOAuthFlow(tenant, requestResponse);
      authenticationTransactionCommandRepository.register(tenant, authenticationTransaction);
    }

    return new Pairs<>(tenant, requestResponse);
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(tenant, authorizationRequestIdentifier.value());

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());

    return oAuthProtocol.getViewData(oAuthViewDataRequest);
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    OAuthSession oAuthSession =
        oAuthSessionDelegate.findOrInitialize(authorizationRequest.sessionKey());

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
    AuthorizationIdentifier authorizationIdentifier =
        authorizationRequestIdentifier.toAuthorizationIdentifier();
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(tenant, authorizationIdentifier);

    AuthenticationInteractionRequestResult result =
        authenticationInteractor.interact(
            tenant,
            authorizationIdentifier,
            type,
            request,
            authenticationTransaction,
            userQueryRepository);

    AuthenticationTransaction updatedTransaction = authenticationTransaction.updateWith(result);
    authenticationTransactionCommandRepository.update(tenant, updatedTransaction);

    if (result.isSuccess()) {
      OAuthSession updated = oAuthSession.didAuthentication(result.user(), result.authentication());
      oAuthSessionDelegate.updateSession(updated);
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

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
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
        DefaultSecurityEventType.federation_request,
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
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    FederationInteractionResult result =
        federationInteractor.callback(
            tenant, federationType, ssoProvider, callbackRequest, userQueryRepository);

    if (result.isError()) {
      return result;
    }

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, result.authorizationRequestIdentifier());

    OAuthSession oAuthSession =
        OAuthSession.create(
            authorizationRequest.sessionKey(),
            result.user(),
            result.authentication(),
            authorizationRequest.maxAge());

    oAuthSessionDelegate.updateSession(oAuthSession);

    eventPublisher.publish(
        tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

    return result;
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);

    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());

    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    User user = session.user();
    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant,
            authorizationRequestIdentifier.value(),
            user,
            authenticationTransaction.isComplete()
                ? session.authentication()
                : new Authentication());

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(oAuthAuthorizeRequest);

    if (authorize.isOk()) {
      userRegistrator.registerOrUpdate(tenant, user);
      eventPublisher.publish(
          tenant,
          authorizationRequest,
          user,
          DefaultSecurityEventType.oauth_authorize,
          requestAttributes);
    } else {
      eventPublisher.publish(
          tenant,
          authorizationRequest,
          user,
          DefaultSecurityEventType.authorize_failure,
          requestAttributes);
    }

    return authorize;
  }

  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(tenant, authorizationRequestIdentifier);
    OAuthSession session = oAuthSessionDelegate.find(authorizationRequest.sessionKey());

    if (Objects.isNull(session)
        || session.isExpire(SystemDateTime.now())
        || Objects.isNull(session.user())) {
      throw new OAuthBadRequestException("invalid_request", "session expired");
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
        DefaultSecurityEventType.oauth_authorize_with_session,
        requestAttributes);

    return authorize;
  }

  public OAuthDenyResponse deny(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());

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
        DefaultSecurityEventType.oauth_deny,
        requestAttributes);

    return denyResponse;
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(tenant, params);

    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProtocolProvider());

    return oAuthProtocol.logout(oAuthLogoutRequest);
  }
}
