package org.idp.sample.presentation.api.oauth;

import jakarta.servlet.http.HttpSession;
import java.util.List;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations")
public class OAuthV1Api implements OAuthRequestDelegate, ParameterTransformable {

  Logger log = LoggerFactory.getLogger(OAuthV1Api.class);
  HttpSession httpSession;
  OAuthApi oAuthApi;
  UserService userService;

  public OAuthV1Api(
      IdpServerApplication idpServerApplication, HttpSession httpSession, UserService userService) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(this);
    this.httpSession = httpSession;
    this.userService = userService;
  }

  @PostMapping("/{id}/authorize")
  public ResponseEntity<?> authorize(
      @PathVariable("tenant-id") String tenantId,
      @PathVariable("id") String id,
      @Validated @RequestBody AuthorizeRequest authorizeRequest) {

    Tenant tenant = Tenant.of(tenantId);
    OAuthSession session = (OAuthSession) httpSession.getAttribute(authorizeRequest.sessionKey());
    UserInteraction userInteraction =
        userInteraction(tenant, authorizeRequest.username(), authorizeRequest.password(), session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            id, tenant.issuer(), userInteraction.user(), userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    switch (authAuthorizeResponse.status()) {
      case OK, REDIRECABLE_BAD_REQUEST -> {
        return new ResponseEntity<>(authAuthorizeResponse.toResponse(), httpHeaders, HttpStatus.OK);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(
            authAuthorizeResponse.toResponse(), httpHeaders, HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(
            authAuthorizeResponse.toResponse(), httpHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/deny")
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

  private UserInteraction userInteraction(
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
