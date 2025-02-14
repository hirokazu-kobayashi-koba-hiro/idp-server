package org.idp.sample.application.service;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.OAuthApi;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.OAuthSessionKey;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.interaction.UserInteraction;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.extension.OAuthDenyReason;
import org.springframework.stereotype.Service;

@Service
public class OAuthFlowService implements OAuthRequestDelegate {

  HttpSession httpSession;
  OAuthApi oAuthApi;
  UserService userService;
  TenantService tenantService;

  public OAuthFlowService(
      IdpServerApplication idpServerApplication,
      HttpSession httpSession,
      UserService userService,
      TenantService tenantService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(this);
    this.httpSession = httpSession;
    this.userService = userService;
    this.tenantService = tenantService;
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

  // FIXME this is bad code
  public OAuthAuthorizeResponse signup(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String username,
      String password,
      String sessionKey) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthSession session = (OAuthSession) httpSession.getAttribute(sessionKey);
    User existingUser = userService.findBy(tenant, username);
    if (existingUser.exists()) {
      throw new RuntimeException("User already exists");
    }
    User user = new User();
    user.setSub(UUID.randomUUID().toString());
    user.setEmail(username);
    user.setPassword(password);
    userService.register(tenant, user);

    UserInteraction userInteraction = interact(tenant, username, password, session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier,
            tenant.issuer(),
            userInteraction.user(),
            userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());

    return oAuthApi.authorize(authAuthorizeRequest);
  }

  // FIXME this is bad code
  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String username,
      String password,
      String sessionKey) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthSession session = (OAuthSession) httpSession.getAttribute(sessionKey);
    UserInteraction userInteraction = interact(tenant, username, password, session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier,
            tenant.issuer(),
            userInteraction.user(),
            userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());

    return oAuthApi.authorize(authAuthorizeRequest);
  }

  // FIXME this is bad code
  public OAuthAuthorizeResponse authorizeWithSession(
          TenantIdentifier tenantIdentifier,
          String oauthRequestIdentifier) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest = oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = (OAuthSession) httpSession.getAttribute(authorizationRequest.sessionKey().key());

    UserInteraction userInteraction = interact(tenant, "", "", session);
    OAuthAuthorizeRequest authAuthorizeRequest =
            new OAuthAuthorizeRequest(
                    oauthRequestIdentifier,
                    tenant.issuer(),
                    userInteraction.user(),
                    userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());

    return oAuthApi.authorize(authAuthorizeRequest);
  }

  public OAuthDenyResponse deny(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(
            oauthRequestIdentifier, tenant.issuer(), OAuthDenyReason.access_denied);

    return oAuthApi.deny(denyRequest);
  }

  private UserInteraction interact(
      Tenant tenant, String username, String password, OAuthSession session) {
    if (Objects.nonNull(session) && !session.isExpire(SystemDateTime.now())) {
      Authentication authentication = session.authentication();
      User user = session.user();
      return new UserInteraction(user, authentication);
    }
    User user = userService.findBy(tenant, username);
    if (userService.authenticate(user, password)) {
      Authentication authentication =
          new Authentication()
              .setTime(SystemDateTime.now())
              .setMethods(List.of("password"))
              .setAcrValues(List.of("urn:mace:incommon:iap:silver"));
      return new UserInteraction(user, authentication);
    }
    throw new IllegalArgumentException("not match password");
  }

  @Override
  public OAuthSession findSession(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    return (OAuthSession) httpSession.getAttribute(sessionKey);
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    String id = httpSession.getId();
    httpSession.getId();
    httpSession.setAttribute(sessionKey, oAuthSession);
    System.out.println(id);
  }
}
