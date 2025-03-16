package org.idp.server.core;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.federation.FederationService;
import org.idp.server.core.function.OAuthFlowFunction;
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
import org.idp.server.core.protcol.OAuthApi;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.sharedsignal.Event;
import org.idp.server.core.sharedsignal.EventPublisher;
import org.idp.server.core.sharedsignal.OAuthFlowEventCreator;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.type.extension.OAuthDenyReason;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.core.user.UserRegistrationService;
import org.idp.server.core.user.UserService;

@Transactional
public class OAuthFlowService implements OAuthFlowFunction {

  OAuthApi oAuthApi;
  OAuthRequestDelegate oAuthRequestDelegate;
  UserService userService;
  OAuthUserInteractors oAuthUserInteractors;
  UserRegistrationService userRegistrationService;
  TenantService tenantService;
  FederationService federationService;
  EventPublisher eventPublisher;

  public OAuthFlowService(
      OAuthApi oAuthApi,
      OAuthRequestDelegate oAuthSessionService,
      OAuthUserInteractors oAuthUserInteractors,
      UserService userService,
      UserRegistrationService userRegistrationService,
      TenantService tenantService,
      FederationService federationService,
      EventPublisher eventPublisher) {
    this.oAuthApi = oAuthApi;
    oAuthApi.setOAuthRequestDelegate(oAuthSessionService);
    this.oAuthRequestDelegate = oAuthSessionService;
    this.oAuthUserInteractors = oAuthUserInteractors;
    this.userService = userService;
    this.userRegistrationService = userRegistrationService;
    this.tenantService = tenantService;
    this.federationService = federationService;
    this.eventPublisher = eventPublisher;
  }

  public Pairs<Tenant, OAuthRequestResponse> request(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());

    OAuthRequestResponse request = oAuthApi.request(oAuthRequest);
    return new Pairs<>(tenant, request);
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(oauthRequestIdentifier, tenant.issuer());

    return oAuthApi.getViewData(oAuthViewDataRequest);
  }

  @Override
  public OAuthUserInteractionResult interact(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      OAuthUserInteractionType type,
      Map<String, Object> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthUserInteractor oAuthUserInteractor = oAuthUserInteractors.get(type);

    OAuthUserInteractionResult result =
        oAuthUserInteractor.interact(tenant, authorizationRequest, type, params, userService);

    if (result.hasUser()) {
      OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());
      OAuthSession updatedSession =
          session.didAuthentication(
              authorizationRequest.sessionKey(), result.user(), result.authentication());
      oAuthRequestDelegate.updateSession(updatedSession);

      publishEvent(authorizationRequest, result.user(), result.eventType());
    }

    return result;
  }

  public FederationRequestResponse requestFederation(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String federationIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    return federationService.request(
        new FederationRequest(oauthRequestIdentifier, tenant.issuer(), federationIdentifier));
  }

  public Pairs<Tenant, FederationCallbackResponse> callbackFederation(
      Map<String, String[]> params) {

    FederationCallbackResponse callbackResponse =
        federationService.callback(new FederationCallbackRequest(params));

    if (callbackResponse.isError()) {
      return Pairs.of(new Tenant(), callbackResponse);
    }

    AuthorizationRequest authorizationRequest =
        oAuthApi.get(callbackResponse.authorizationRequestIdentifier());

    Tenant tenant = tenantService.find(authorizationRequest.tokenIssuer());

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

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());

    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier, tenant.issuer(), session.user(), session.authentication());

    User user = userRegistrationService.registerOrUpdate(tenant, session.user());

    OAuthAuthorizeResponse authorize = oAuthApi.authorize(oAuthAuthorizeRequest);

    publishEvent(authorizationRequest, user, DefaultEventType.login);

    return authorize;
  }

  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthRequestDelegate.findSession(authorizationRequest.sessionKey());

    if (Objects.isNull(session)
        || session.isExpire(SystemDateTime.now())
        || Objects.isNull(session.user())) {
      throw new OAuthBadRequestException("invalid_request", "session expired");
    }

    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier, tenant.issuer(), session.user(), session.authentication());

    OAuthAuthorizeResponse authorize = oAuthApi.authorize(authAuthorizeRequest);

    publishEvent(authorizationRequest, session.user(), DefaultEventType.login_with_session);

    return authorize;
  }

  public OAuthDenyResponse deny(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(
            oauthRequestIdentifier, tenant.issuer(), OAuthDenyReason.access_denied);

    return oAuthApi.deny(denyRequest);
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(params, tenant.issuer());

    return oAuthApi.logout(oAuthLogoutRequest);
  }

  private void publishEvent(
      AuthorizationRequest authorizationRequest, User user, DefaultEventType type) {
    OAuthFlowEventCreator eventCreator =
        new OAuthFlowEventCreator(authorizationRequest, user, type);
    Event event = eventCreator.create();
    eventPublisher.publish(event);
  }
}
