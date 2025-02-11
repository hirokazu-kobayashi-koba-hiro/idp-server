package org.idp.sample.presentation.api.oauth;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.idp.sample.application.service.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.presentation.api.ParameterTransformable;
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
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations")
public class OAuthV1Api implements OAuthRequestDelegate, ParameterTransformable {

  Logger log = LoggerFactory.getLogger(OAuthV1Api.class);
  HttpSession httpSession;
  OAuthApi oAuthApi;
  UserService userService;
  TenantService tenantService;

  public OAuthV1Api(
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

  @GetMapping
  public ResponseEntity<?> get(
          @RequestParam(required = false) MultiValueMap<String, String> request,
          @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> params = transform(request);
    Tenant tenant = tenantService.get(tenantId);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());
    OAuthRequestResponse response = oAuthApi.request(oAuthRequest);
    switch (response.status()) {
      case OK, OK_SESSION_ENABLE -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.OK);
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("location", response.redirectUri());
        return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @GetMapping("/{id}/view-data")
  public ResponseEntity<?> getViewData(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {
    Tenant tenant = tenantService.get(tenantId);
    OAuthViewDataRequest oAuthViewDataRequest = new OAuthViewDataRequest(id, tenant.issuer());

    OAuthViewDataResponse viewDataResponse = oAuthApi.getViewData(oAuthViewDataRequest);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(viewDataResponse.contents(), httpHeaders, HttpStatus.OK);
  }

  // TODO
  @PostMapping("/{id}/signup")
  public ResponseEntity<?> signup(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @Validated @RequestBody PasswordAuthenticationRequest passwordAuthenticationRequest) {

    Tenant tenant = tenantService.get(tenantId);
    OAuthSession session =
        (OAuthSession) httpSession.getAttribute(passwordAuthenticationRequest.sessionKey());
    User existingUser = userService.findBy(tenant, passwordAuthenticationRequest.username());
    if (existingUser.exists()) {
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    }
    User user = new User();
    user.setSub(UUID.randomUUID().toString());
    user.setEmail(passwordAuthenticationRequest.username());
    user.setPassword(passwordAuthenticationRequest.password());
    userService.register(tenant, user);

    UserInteraction userInteraction =
        userInteraction(
            tenant,
            passwordAuthenticationRequest.username(),
            passwordAuthenticationRequest.password(),
            session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            id, tenant.issuer(), userInteraction.user(), userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    switch (authAuthorizeResponse.status()) {
      case OK, REDIRECABLE_BAD_REQUEST -> {
        return new ResponseEntity<>(authAuthorizeResponse.contents(), httpHeaders, HttpStatus.OK);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(
            authAuthorizeResponse.contents(), httpHeaders, HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(
            authAuthorizeResponse.contents(), httpHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/authorize")
  public ResponseEntity<?> authorize(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @Validated @RequestBody PasswordAuthenticationRequest passwordAuthenticationRequest) {

    Tenant tenant = tenantService.get(tenantId);
    OAuthSession session =
        (OAuthSession) httpSession.getAttribute(passwordAuthenticationRequest.sessionKey());
    UserInteraction userInteraction =
        userInteraction(
            tenant,
            passwordAuthenticationRequest.username(),
            passwordAuthenticationRequest.password(),
            session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            id, tenant.issuer(), userInteraction.user(), userInteraction.authentication());
    httpSession.setAttribute("id", httpSession.getId());

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthApi.authorize(authAuthorizeRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    switch (authAuthorizeResponse.status()) {
      case OK, REDIRECABLE_BAD_REQUEST -> {
        return new ResponseEntity<>(authAuthorizeResponse.contents(), httpHeaders, HttpStatus.OK);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(
            authAuthorizeResponse.contents(), httpHeaders, HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(
            authAuthorizeResponse.contents(), httpHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/deny")
  public ResponseEntity<?> deny(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {
    Tenant tenant = tenantService.get(tenantId);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(id, tenant.issuer(), OAuthDenyReason.access_denied);

    OAuthDenyResponse oAuthDenyResponse = oAuthApi.deny(denyRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    switch (oAuthDenyResponse.status()) {
      case OK, REDIRECABLE_BAD_REQUEST -> {
        return new ResponseEntity<>(oAuthDenyResponse.contents(), httpHeaders, HttpStatus.OK);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(
            oAuthDenyResponse.contents(), httpHeaders, HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(
            oAuthDenyResponse.contents(), httpHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
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
