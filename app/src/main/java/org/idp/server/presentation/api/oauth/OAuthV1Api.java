package org.idp.server.presentation.api.oauth;

import static org.idp.server.core.handler.oauth.io.OAuthRequestStatus.OK_ACCOUNT_CREATION;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.application.service.OAuthFlowService;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.presentation.api.ParameterTransformable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations")
public class OAuthV1Api implements ParameterTransformable {

  OAuthFlowService oAuthFlowService;
  String adminAuthViewUrl;
  String authViewUrl;

  public OAuthV1Api(
      OAuthFlowService oAuthFlowService,
      @Value("${idp.configurations.adminAuthViewUrl}") String adminAuthViewUrl,
      @Value("${idp.configurations.authViewUrl}") String authViewUrl) {
    this.oAuthFlowService = oAuthFlowService;
    this.adminAuthViewUrl = adminAuthViewUrl;
    this.authViewUrl = authViewUrl;
  }

  @GetMapping
  public ResponseEntity<?> get(
      HttpServletRequest httpServletRequest,
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    System.out.println(httpServletRequest.getHeader("X-Forwarded-For"));
    System.out.println(httpServletRequest.getHeader("X-Real-IP"));
    System.out.println(httpServletRequest.getRemoteAddr());

    Map<String, String[]> params = transform(request);
    Pairs<Tenant, OAuthRequestResponse> result = oAuthFlowService.request(tenantId, params);

    Tenant tenant = result.getLeft();
    OAuthRequestResponse response = result.getRight();

    switch (response.status()) {
      case OK, OK_SESSION_ENABLE, OK_ACCOUNT_CREATION -> {
        String url = createUrl(tenant, response);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, url);

        return new ResponseEntity<>(headers, HttpStatus.FOUND);
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(HttpHeaders.LOCATION, response.redirectUri());

        return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
      }
      default -> {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(HttpHeaders.LOCATION, createErrorUrl(tenant, response));

        return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
      }
    }
  }

  private String createUrl(Tenant tenant, OAuthRequestResponse response) {
    String url = tenant.isAdmin() ? adminAuthViewUrl : authViewUrl;

    if (response.status() == OK_ACCOUNT_CREATION) {
      return String.format(
          url + "signup?id=%s&tenant_id=%s",
          response.authorizationRequestId(),
          tenant.identifier().value());
    }

    return String.format(
        url + "signin/webauthn?id=%s&tenant_id=%s",
        response.authorizationRequestId(),
        tenant.identifier().value());
  }

  private String createErrorUrl(Tenant tenant, OAuthRequestResponse response) {
    String url = tenant.isAdmin() ? adminAuthViewUrl : authViewUrl;

    return String.format(
        url + "error/?error=%s&error_description=%s",
        response.error(),
        response.errorDescription());
  }

  @GetMapping("/{id}/view-data")
  public ResponseEntity<?> getViewData(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {

    OAuthViewDataResponse viewDataResponse = oAuthFlowService.getViewData(tenantId, id);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(viewDataResponse.contents(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/federations")
  public ResponseEntity<?> createFederation(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") String id,
      @RequestBody Map<String, String> body) {

    if (!body.containsKey("federatable_idp_id")) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    FederationRequestResponse requestResponse =
        oAuthFlowService.requestFederation(tenantIdentifier, id, body.get("federatable_idp_id"));

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");

    return new ResponseEntity<>(
        Map.of("redirect_uri", requestResponse.authorizationRequestUrl()), headers, HttpStatus.OK);
  }

  @PostMapping("/{id}/federations/callback")
  public ResponseEntity<?> callbackFederation(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @RequestBody(required = false) MultiValueMap<String, String> body) {

    Map<String, String[]> params = transform(body);
    FederationCallbackResponse callbackResponse =
        oAuthFlowService.callbackFederation(tenantId, id, params);

    switch (callbackResponse.status()) {
      case OK -> {
        return new ResponseEntity<>(HttpStatus.OK);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/signup")
  public ResponseEntity<?> signup(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @Validated @RequestBody UserRegistrationRequest request) {

    User user = oAuthFlowService.requestSignup(tenantId, id, request.toUserRegistration());

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    Map<String, Object> response = new HashMap<>();
    response.put("id", user.sub());
    response.put("name", user.name());
    response.put("email", user.email());

    return new ResponseEntity<>(response, httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/authorize-with-session")
  public ResponseEntity<?> authorizeWithSession(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {

    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowService.authorizeWithSession(tenantId, id);

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

  @PostMapping("/{id}/password-authentication")
  public ResponseEntity<?> authenticateWithPassword(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @Validated @RequestBody PasswordAuthenticationRequest passwordAuthenticationRequest) {

    oAuthFlowService.authenticateWithPassword(
        tenantId,
        id,
        passwordAuthenticationRequest.username(),
        passwordAuthenticationRequest.password());

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/authorize")
  public ResponseEntity<?> authorize(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthFlowService.authorize(tenantId, id);

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

    OAuthDenyResponse oAuthDenyResponse = oAuthFlowService.deny(tenantId, id);
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
}
