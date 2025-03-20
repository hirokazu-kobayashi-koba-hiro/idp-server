package org.idp.server.core;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.api.OAuthFlowApi;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.federation.FederationService;
import org.idp.server.core.handler.federation.io.FederationCallbackRequest;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.interaction.*;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.protocol.OAuthProtocol;
import org.idp.server.core.sharedsignal.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.extension.OAuthDenyReason;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.core.user.UserRegistrationService;
import org.idp.server.core.user.UserRepository;

@Transactional
public class OAuthFlowEntryService implements OAuthFlowApi {

  OAuthProtocol oAuthProtocol;
  OAuthRequestDelegate oAuthRequestDelegate;
  UserRepository userRepository;
  OAuthUserInteractors oAuthUserInteractors;
  UserRegistrationService userRegistrationService;
  TenantRepository tenantRepository;
  FederationService federationService;
  OAuthFlowEventPublisher eventPublisher;

  public OAuthFlowEntryService(
      OAuthProtocol oAuthProtocol,
      OAuthRequestDelegate oAuthSessionService,
      OAuthUserInteractors oAuthUserInteractors,
      UserRepository userRepository,
      UserRegistrationService userRegistrationService,
      TenantRepository tenantRepository,
      FederationService federationService,
      OAuthFlowEventPublisher eventPublisher) {
    this.oAuthProtocol = oAuthProtocol;
    oAuthProtocol.setOAuthRequestDelegate(oAuthSessionService);
    this.oAuthRequestDelegate = oAuthSessionService;
    this.oAuthUserInteractors = oAuthUserInteractors;
    this.userRepository = userRepository;
    this.userRegistrationService = userRegistrationService;
    this.tenantRepository = tenantRepository;
    this.federationService = federationService;
    this.eventPublisher = eventPublisher;
  }

  public Pairs<Tenant, OAuthRequestResponse> request(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

    OAuthRequestResponse request = oAuthProtocol.request(oAuthRequest);
    return new Pairs<>(tenant, request);
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(tenant, oauthRequestIdentifier);

    return oAuthProtocol.getViewData(oAuthViewDataRequest);
  }

  @Override
  public OAuthUserInteractionResult interact(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      OAuthUserInteractionType type,
      Map<String, Object> params) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());

    OAuthUserInteractor oAuthUserInteractor = oAuthUserInteractors.get(type);

    OAuthUserInteractionResult result =
        oAuthUserInteractor.interact(tenant, oAuthSession, type, params, userRepository);

    if (result.hasUser()) {
      OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());
      OAuthSession updatedSession =
          session.didAuthentication(
              authorizationRequest.sessionKey(), result.user(), result.authentication());
      oAuthRequestDelegate.updateSession(updatedSession);

      eventPublisher.publish(tenant, authorizationRequest, result.user(), result.eventType());
    }

    return result;
  }

  public FederationRequestResponse requestFederation(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String federationIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    oAuthProtocol.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    return federationService.request(
        new FederationRequest(tenant, oauthRequestIdentifier, federationIdentifier));
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
        new OAuthSession(
            authorizationRequest.sessionKey(),
            callbackResponse.user(),
            new Authentication().setTime(SystemDateTime.now()),
            SystemDateTime.now().plusSeconds(3600));

    oAuthRequestDelegate.updateSession(oAuthSession);

    return Pairs.of(tenant, callbackResponse);
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());

    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant, oauthRequestIdentifier, session.user(), session.authentication());

    User user = userRegistrationService.registerOrUpdate(tenant, session.user());

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(oAuthAuthorizeRequest);

    eventPublisher.publish(tenant, authorizationRequest, user, DefaultEventType.login);

    return authorize;
  }

  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthProtocol.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());

    if (Objects.isNull(session)
        || session.isExpire(SystemDateTime.now())
        || Objects.isNull(session.user())) {
      throw new OAuthBadRequestException("invalid_request", "session expired");
    }

    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            tenant, oauthRequestIdentifier, session.user(), session.authentication());

    OAuthAuthorizeResponse authorize = oAuthProtocol.authorize(authAuthorizeRequest);

    eventPublisher.publish(
        tenant, authorizationRequest, session.user(), DefaultEventType.login_with_session);

    return authorize;
  }

  public OAuthDenyResponse deny(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(tenant, oauthRequestIdentifier, OAuthDenyReason.access_denied);

    return oAuthProtocol.deny(denyRequest);
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(tenant, params);

    return oAuthProtocol.logout(oAuthLogoutRequest);
  }
}
