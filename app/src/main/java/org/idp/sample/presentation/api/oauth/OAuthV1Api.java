package org.idp.sample.presentation.api.oauth;

import static org.idp.server.handler.oauth.io.OAuthRequestStatus.OK_ACCOUNT_CREATION;

import java.util.HashMap;
import java.util.Map;
import org.idp.sample.application.service.OAuthFlowService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.extension.Pairs;
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
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

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
      case BAD_REQUEST -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(response.contents(), HttpStatus.INTERNAL_SERVER_ERROR);
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

  @GetMapping("/{id}/view-data")
  public ResponseEntity<?> getViewData(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {

    OAuthViewDataResponse viewDataResponse = oAuthFlowService.getViewData(tenantId, id);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(viewDataResponse.contents(), httpHeaders, HttpStatus.OK);
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
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @RequestBody Map<String, String> requestBody) {

    String action = requestBody.getOrDefault("action", "signin");

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthFlowService.authorize(tenantId, id, action);

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
