package org.idp.server.adapters.springboot.restapi.oauth;

import static org.idp.server.core.oidc.io.OAuthRequestStatus.OK_ACCOUNT_CREATION;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.authentication.AuthenticationInteractionType;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.SsoProvider;
import org.idp.server.core.federation.io.FederationRequestResponse;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.io.OAuthAuthorizeResponse;
import org.idp.server.core.oidc.io.OAuthDenyResponse;
import org.idp.server.core.oidc.io.OAuthRequestResponse;
import org.idp.server.core.oidc.io.OAuthViewDataResponse;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.usecases.IdpServerApplication;
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
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
    this.adminAuthViewUrl = adminAuthViewUrl;
    this.authViewUrl = authViewUrl;
  }

  @GetMapping
  public ResponseEntity<?> get(
      HttpServletRequest httpServletRequest,
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    Pairs<Tenant, OAuthRequestResponse> result =
        oAuthFlowApi.request(tenantId, params, requestAttributes);

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
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthViewDataResponse viewDataResponse =
        oAuthFlowApi.getViewData(tenantId, authorizationRequestIdentifier, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(viewDataResponse.contents(), httpHeaders, HttpStatus.OK);
  }

  @PostMapping("/{id}/federations/{federation-type}/{sso-provider-name}")
  public ResponseEntity<?> createFederation(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      @PathVariable("federation-type") FederationType federationType,
      @PathVariable("sso-provider-name") SsoProvider ssoProvider,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    FederationRequestResponse requestResponse =
        oAuthFlowApi.requestFederation(
            tenantIdentifier,
            authorizationRequestIdentifier,
            federationType,
            ssoProvider,
            requestAttributes);

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

  @PostMapping("/{id}/{mfa-interaction-type}")
  public ResponseEntity<?> interact(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      @PathVariable("mfa-interaction-type")
          AuthenticationInteractionType authenticationInteractionType,
      @RequestBody(required = false) Map<String, Object> params,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationInteractionRequestResult result =
        oAuthFlowApi.interact(
            tenantId,
            authorizationRequestIdentifier,
            authenticationInteractionType,
            new AuthenticationInteractionRequest(params),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");

    switch (result.status()) {
      case SUCCESS -> {
        return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.OK);
      }
      case CLIENT_ERROR -> {
        return new ResponseEntity<>(result.response(), httpHeaders, HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(
            result.response(), httpHeaders, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/authorize-with-session")
  public ResponseEntity<?> authorizeWithSession(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowApi.authorizeWithSession(
            tenantId, authorizationRequestIdentifier, requestAttributes);

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
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowApi.authorize(tenantId, authorizationRequestIdentifier, requestAttributes);

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
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthDenyResponse oAuthDenyResponse =
        oAuthFlowApi.deny(tenantId, authorizationRequestIdentifier, requestAttributes);
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
