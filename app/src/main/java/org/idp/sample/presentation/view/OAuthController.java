package org.idp.sample.presentation.view;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.sample.presentation.api.Tenant;
import org.idp.sample.user.UserService;
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
import org.idp.server.type.extension.OAuthDenyReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping
public class OAuthController implements OAuthRequestDelegate, ParameterTransformable {

  Logger log = LoggerFactory.getLogger(OAuthController.class);
  HttpSession httpSession;
  OAuthApi oAuthApi;
  UserService userService;

  public OAuthController(
      IdpServerApplication idpServerApplication, HttpSession httpSession, UserService userService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(this);
    this.httpSession = httpSession;
    this.userService = userService;
  }

  @GetMapping("{tenant-id}/v1/authorizations")
  public String get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") String tenantId,
      Model model) {
    Map<String, String[]> params = transform(request);
    Tenant tenant = Tenant.of(tenantId);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());
    OAuthRequestResponse response = oAuthApi.request(oAuthRequest);
    switch (response.status()) {
      case OK -> {
        log.info("sessionEnable: false");
        model.addAttribute("sessionEnable", false);
        model.addAttribute("sessionKey", response.sessionKey());
        model.addAttribute("id", response.authorizationRequestId());
        model.addAttribute("tenantId", tenant.id());
        model.addAttribute("clientName", response.clientConfiguration().clientName());
        model.addAttribute("scopes", response.scopeList());
        return "authorizations";
      }
      case OK_SESSION_ENABLE -> {
        log.info("sessionEnable: true");
        model.addAttribute("sessionEnable", true);
        model.addAttribute("sessionKey", response.sessionKey());
        model.addAttribute("id", response.authorizationRequestId());
        model.addAttribute("tenantId", tenant.id());
        model.addAttribute("clientName", response.clientConfiguration().clientName());
        model.addAttribute("scopes", response.scopeList());
        return "authorizations";
      }
      case OK_ACCOUNT_CREATION -> {
        log.info("request creation account");
        model.addAttribute("sessionEnable", false);
        model.addAttribute("sessionKey", response.sessionKey());
        model.addAttribute("id", response.authorizationRequestId());
        model.addAttribute("tenantId", tenant.id());
        model.addAttribute("clientName", response.clientConfiguration().clientName());
        model.addAttribute("scopes", response.scopeList());
        return "user";
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        log.info("redirect");
        return "redirect:" + response.redirectUri();
      }
      default -> {
        model.addAttribute("error", response.error());
        model.addAttribute("errorDescription", response.errorDescription());
        return "error";
      }
    }
  }

  @GetMapping("/v1/authorize")
  public String showSuccessPage(
      @ModelAttribute("tenantId") String tenantId,
      @ModelAttribute("sessionKey") String sessionKey,
      @ModelAttribute("id") String id,
      Model model) {
    Tenant tenant = Tenant.of("123");
    model.addAttribute("tenantId", tenant.id());
    model.addAttribute("sessionKey", sessionKey);
    model.addAttribute("id", id);
    model.addAttribute("tenantId", tenant.id());
    return "authorize";
  }

  @PostMapping("/v1/authorize")
  public String authorize(
      @ModelAttribute("sessionKey") String sessionKey,
      @ModelAttribute("username") String username,
      @ModelAttribute("password") String password,
      @ModelAttribute("id") String id,
      @ModelAttribute("tenantId") String tenantId,
      Model model) {

    Tenant tenant = Tenant.of(tenantId);
    OAuthSession session = (OAuthSession) httpSession.getAttribute(sessionKey);
    UserInteraction userInteraction = userInteraction(username, password, session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            id, tenant.issuer(), userInteraction.user(), userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());
    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    switch (authAuthorizeResponse.status()) {
      case OK, REDIRECABLE_BAD_REQUEST -> {
        return "redirect:" + authAuthorizeResponse.redirectUriValue();
      }
      default -> {
        model.addAttribute("error", authAuthorizeResponse.error());
        model.addAttribute("errorDescription", authAuthorizeResponse.errorDescription());
        return "error";
      }
    }
  }

  @PostMapping("/v1/deny")
  public String deny(
      @ModelAttribute("id") String id, @ModelAttribute("tenantId") String tenantId, Model model) {
    Tenant tenant = Tenant.of(tenantId);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(id, tenant.issuer(), OAuthDenyReason.access_denied);
    OAuthDenyResponse oAuthDenyResponse = oAuthApi.deny(denyRequest);
    switch (oAuthDenyResponse.status()) {
      case OK, REDIRECABLE_BAD_REQUEST -> {
        return "redirect:" + oAuthDenyResponse.redirectUriValue();
      }
      default -> {
        model.addAttribute("error", oAuthDenyResponse.error());
        model.addAttribute("errorDescription", oAuthDenyResponse.errorDescription());
        return "error";
      }
    }
  }

  private UserInteraction userInteraction(String username, String password, OAuthSession session) {
    if (Objects.nonNull(session) && !session.isExpire(SystemDateTime.now())) {
      Authentication authentication = session.authentication();
      User user = session.user();
      return new UserInteraction(user, authentication);
    }
    User user = userService.get(username);
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
