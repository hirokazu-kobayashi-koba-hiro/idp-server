package org.idp.server.adapters.springboot.restapi.oauth;

import static org.idp.server.core.oauth.io.OAuthRequestStatus.OK_ACCOUNT_CREATION;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.OAuthFlowApi;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.mfa.MfaInteractionResult;
import org.idp.server.core.mfa.StandardMfaInteractionType;
import org.idp.server.core.oauth.io.OAuthAuthorizeResponse;
import org.idp.server.core.oauth.io.OAuthDenyResponse;
import org.idp.server.core.oauth.io.OAuthRequestResponse;
import org.idp.server.core.oauth.io.OAuthViewDataResponse;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.Pairs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/authorizations")
public class OAuthV1Api implements ParameterTransformable {

  OAuthFlowApi oAuthFlowApi;
  String adminAuthViewUrl;
  String authViewUrl;

  public OAuthV1Api(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.adminAuthViewUrl}") String adminAuthViewUrl,
      @Value("${idp.configurations.authViewUrl}") String authViewUrl) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowFunction();
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
    Pairs<Tenant, OAuthRequestResponse> result = oAuthFlowApi.request(tenantId, params);

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

  // TODO move to core logic
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

  // TODO move to core logic
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

    OAuthViewDataResponse viewDataResponse = oAuthFlowApi.getViewData(tenantId, id);

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
        oAuthFlowApi.requestFederation(tenantIdentifier, id, body.get("federatable_idp_id"));

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");

    switch (requestResponse.status()) {
      case REDIRECABLE_OK, REDIRECABLE_BAD_REQUEST -> {
        return new ResponseEntity<>(
            Map.of("redirect_uri", requestResponse.authorizationRequestUrl()),
            headers,
            HttpStatus.OK);
      }
      default -> {
        return new ResponseEntity<>(
            Map.of("error", "unexpected error is occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/signup")
  public ResponseEntity<?> signup(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") String id,
      @RequestBody Map<String, Object> params) {

    MfaInteractionResult result =
        oAuthFlowApi.interact(
            tenantId, id, StandardMfaInteractionType.PASSWORD_REGISTRATION.toType(), params);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/authorize-with-session")
  public ResponseEntity<?> authorizeWithSession(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthFlowApi.authorizeWithSession(tenantId, id);

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
      @RequestBody Map<String, Object> params) {

    MfaInteractionResult result =
        oAuthFlowApi.interact(
            tenantId, id, StandardMfaInteractionType.PASSWORD_AUTHENTICATION.toType(), params);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/authorize")
  public ResponseEntity<?> authorize(
      @PathVariable("tenant-id") TenantIdentifier tenantId, @PathVariable("id") String id) {

    OAuthAuthorizeResponse authAuthorizeResponse = oAuthFlowApi.authorize(tenantId, id);

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

    OAuthDenyResponse oAuthDenyResponse = oAuthFlowApi.deny(tenantId, id);
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
