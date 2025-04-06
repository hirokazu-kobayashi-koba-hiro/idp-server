package org.idp.server.core;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.api.OAuthFlowApi;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.federation.FederationService;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRegistrationService;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.oauth.io.*;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.protocol.OAuthProtocol;
import org.idp.server.core.security.*;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.extension.OAuthDenyReason;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.core.type.security.RequestAttributes;

@Transactional
public class OAuthFlowEntryService implements OAuthFlowApi {

  OAuthProtocol oAuthProtocol;
  OAuthRequestDelegate oAuthRequestDelegate;
  UserRepository userRepository;
  AuthenticationInteractors authenticationInteractors;
  UserRegistrationService userRegistrationService;
  TenantRepository tenantRepository;
  FederationService federationService;
  OAuthFlowEventPublisher eventPublisher;

  public OAuthFlowEntryService(
      OAuthProtocol oAuthProtocol,
      OAuthRequestDelegate oAuthSessionService,
      AuthenticationInteractors authenticationInteractors,
      UserRepository userRepository,
      UserRegistrationService userRegistrationService,
      TenantRepository tenantRepository,
      FederationService federationService,
      OAuthFlowEventPublisher eventPublisher) {
    this.oAuthProtocol = oAuthProtocol;
    this.oAuthRequestDelegate = oAuthSessionService;
    this.authenticationInteractors = authenticationInteractors;
    this.userRepository = userRepository;
    this.userRegistrationService = userRegistrationService;
    this.tenantRepository = tenantRepository;
    this.federationService = federationService;
    this.eventPublisher = eventPublisher;
  }

  public Pairs<Tenant, OAuthRequestResponse> request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

    OAuthRequestResponse request = oAuthProtocol.request(oAuthRequest);
    return new Pairs<>(tenant, request);
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(tenant, authorizationRequestIdentifier.value());

    return oAuthProtocol.getViewData(oAuthViewDataRequest);
  }

  @Override
  public AuthenticationInteractionResult interact(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest = oAuthProtocol.get(authorizationRequestIdentifier);
    OAuthSession oAuthSession =
        oAuthRequestDelegate.findOrInitialize(authorizationRequest.sessionKey());

    AuthenticationInteractor authenticationInteractor = authenticationInteractors.get(type);
    AuthenticationTransactionIdentifier authenticationTransactionIdentifier =
        new AuthenticationTransactionIdentifier(authorizationRequestIdentifier.value());
    AuthenticationInteractionResult result =
        authenticationInteractor.interact(
            tenant,
            authenticationTransactionIdentifier,
            type,
            request,
            oAuthSession,
            userRepository);

    if (result.isSuccess()) {
      OAuthSession updated = oAuthSession.didAuthentication(result.user(), result.authentication());
      oAuthRequestDelegate.updateSession(updated);
    }

    eventPublisher.publish(
        tenant, authorizationRequest, result.user(), result.eventType(), requestAttributes);

    return result;
  }

  public FederationRequestResponse requestFederation(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      String federationIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    oAuthProtocol.get(authorizationRequestIdentifier);

    return federationService.request(
        new FederationRequest(
            tenant, authorizationRequestIdentifier.value(), federationIdentifier));
  }

  public Pairs<Tenant, FederationCallbackResponse> callbackFederation(
      Map<String, String[]> params) {

    FederationCallbackResponse callbackResponse =
        federationService.callback(new FederationCallbackRequest(params));

    if (callbackResponse.isError()) {
      return Pairs.of(new Tenant(), callbackResponse);
    }

    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(callbackResponse.authorizationRequestIdentifier());

    Tenant tenant = tenantRepository.get(authorizationRequest.tenantIdentifier());

    OAuthSession oAuthSession =
        OAuthSession.create(
            authorizationRequest.sessionKey(),
            callbackResponse.user(),
            new Authentication().setTime(SystemDateTime.now()),
            authorizationRequest.maxAge());

    oAuthRequestDelegate.updateSession(oAuthSession);

    return Pairs.of(tenant, callbackResponse);
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest = oAuthProtocol.get(authorizationRequestIdentifier);
    OAuthSession session = oAuthRequestDelegate.find(authorizationRequest.sessionKey());

    User updatedUser = userRegistrationService.registerOrUpdate(tenant, session.user());

    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant, authorizationRequestIdentifier.value(), updatedUser, session.authentication());

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(oAuthAuthorizeRequest);

    eventPublisher.publish(
        tenant,
        authorizationRequest,
        updatedUser,
        DefaultSecurityEventType.login,
        requestAttributes);

    return authorize;
  }

  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest = oAuthProtocol.get(authorizationRequestIdentifier);
    OAuthSession session = oAuthRequestDelegate.find(authorizationRequest.sessionKey());

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
        DefaultSecurityEventType.login_with_session,
        requestAttributes);

    return authorize;
  }

  public OAuthDenyResponse deny(
      TenantIdentifier tenantIdentifier,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(
            tenant, authorizationRequestIdentifier.value(), OAuthDenyReason.access_denied);

    return oAuthProtocol.deny(denyRequest);
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(tenant, params);

    return oAuthProtocol.logout(oAuthLogoutRequest);
  }
}
