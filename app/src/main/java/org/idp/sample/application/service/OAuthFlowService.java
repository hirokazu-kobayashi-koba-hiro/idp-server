package org.idp.sample.application.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.sample.application.service.authentication.WebAuthnService;
import org.idp.sample.application.service.authorization.OAuthSessionService;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.application.service.user.UserAuthenticationService;
import org.idp.sample.application.service.user.UserRegistrationService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.domain.model.user.UserRegistration;
import org.idp.sample.subdomain.webauthn.WebAuthnCredential;
import org.idp.sample.subdomain.webauthn.WebAuthnSession;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.OAuthApi;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.interaction.UserInteraction;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.extension.OAuthDenyReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OAuthFlowService {

  OAuthApi oAuthApi;
  OAuthSessionService oAuthSessionService;
  UserRegistrationService userRegistrationService;
  UserAuthenticationService userAuthenticationService;
  TenantService tenantService;
  WebAuthnService webAuthnService;

  public OAuthFlowService(
      IdpServerApplication idpServerApplication,
      OAuthSessionService oAuthSessionService,
      UserRegistrationService userRegistrationService,
      UserAuthenticationService userAuthenticationService,
      TenantService tenantService,
      WebAuthnService webAuthnService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(oAuthSessionService);
    this.oAuthSessionService = oAuthSessionService;
    this.userRegistrationService = userRegistrationService;
    this.userAuthenticationService = userAuthenticationService;
    this.tenantService = tenantService;
    this.webAuthnService = webAuthnService;
  }

  public OAuthRequestResponse request(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());

    return oAuthApi.request(oAuthRequest);
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(oauthRequestIdentifier, tenant.issuer());

    return oAuthApi.getViewData(oAuthViewDataRequest);
  }

  public void requestSignup(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      UserRegistration userRegistration) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    User user = userRegistrationService.create(tenant, userRegistration);

    OAuthSession oAuthSession =
        new OAuthSession(
            authorizationRequest.sessionKey(),
            user,
            new Authentication(),
            SystemDateTime.now().plusSeconds(3600));
    oAuthSessionService.registerSession(oAuthSession);
  }

  public WebAuthnSession challengeWebAuthnRegistration(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    WebAuthnSession webAuthnSession = webAuthnService.challengeRegistration(tenant);

    return webAuthnSession;
  }

  public void verifyWebAuthnRegistration(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, String request) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    WebAuthnCredential webAuthnCredential =
        webAuthnService.verifyRegistration(tenant, oAuthSession.user(), request);

    oAuthSessionService.updateSession(oAuthSession);
  }

  public void authenticateWithPassword(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String username,
      String password) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    User user = userAuthenticationService.authenticateWithPassword(tenant, username, password);
    OAuthSession session = oAuthSessionService.findSession(authorizationRequest.sessionKey());
    OAuthSession updatedSession =
        session.didAuthenticationPassword(authorizationRequest.sessionKey(), user);

    oAuthSessionService.updateSession(updatedSession);
  }

  public WebAuthnSession challengeWebAuthnAuthentication(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    WebAuthnSession webAuthnSession = webAuthnService.challengeAuthentication(tenant);

    return webAuthnSession;
  }

  public void verifyWebAuthnAuthentication(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, String request) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    User user = webAuthnService.verifyAuthentication(tenant, oAuthSession.user(), request);
    OAuthSession didWebAuthnAuthenticationSession =
        oAuthSession.didWebAuthnAuthentication(authorizationRequest.sessionKey(), user);

    oAuthSessionService.updateSession(didWebAuthnAuthenticationSession);
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier, tenant.issuer(), session.user(), session.authentication());

    if (authorizationRequest.isPromptCreate()) {
      userRegistrationService.register(tenant, session.user());
    }

    return oAuthApi.authorize(oAuthAuthorizeRequest);
  }

  // FIXME this is bad code
  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    UserInteraction userInteraction = interact(tenant, "", "", session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier,
            tenant.issuer(),
            userInteraction.user(),
            userInteraction.authentication());

    return oAuthApi.authorize(authAuthorizeRequest);
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

  private UserInteraction interact(
      Tenant tenant, String username, String password, OAuthSession session) {

    if (Objects.nonNull(session) && !session.isExpire(SystemDateTime.now())) {
      Authentication authentication = session.authentication();
      User user = session.user();
      return new UserInteraction(user, authentication);
    }

    User authenticated =
        userAuthenticationService.authenticateWithPassword(tenant, username, password);
    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(List.of("pwd"))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));
    return new UserInteraction(authenticated, authentication);
  }
}
