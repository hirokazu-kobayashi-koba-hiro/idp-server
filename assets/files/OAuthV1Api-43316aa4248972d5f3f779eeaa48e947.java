/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.adapters.springboot.application.restapi.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.SecurityHeaderConfigurable;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.federation.FederationInteractionResult;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.io.FederationCallbackRequest;
import org.idp.server.core.openid.federation.io.FederationRequestResponse;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.oauth.OAuthFlowApi;
import org.idp.server.core.openid.oauth.io.*;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/authorizations")
public class OAuthV1Api implements ParameterTransformable, SecurityHeaderConfigurable {

  OAuthFlowApi oAuthFlowApi;

  public OAuthV1Api(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
  }

  @PostMapping(value = "/push", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<?> push(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @RequestParam(required = false) MultiValueMap<String, String> request,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthPushedRequestResponse response =
        oAuthFlowApi.push(
            tenantIdentifier, params, authorizationHeader, clientCert, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    return new ResponseEntity<>(response.contents(), httpHeaders, HttpStatus.OK);
  }

  @GetMapping
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(required = false) MultiValueMap<String, String> request,
      HttpServletRequest httpServletRequest) {

    return handleAuthorizationRequest(tenantIdentifier, request, httpServletRequest);
  }

  @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(required = false) MultiValueMap<String, String> request,
      HttpServletRequest httpServletRequest) {

    return handleAuthorizationRequest(tenantIdentifier, request, httpServletRequest);
  }

  /**
   * Handle authorization request (common logic for GET and POST).
   *
   * <p>RFC 6749 Section 3.1: The authorization server MUST support the use of the HTTP "GET" method
   * for the authorization endpoint and MAY support the use of the "POST" method as well.
   *
   * <p>OIDC Core 1.0 Section 3.1.2.1: Authorization Servers MUST support the use of the HTTP GET
   * and POST methods at the Authorization Endpoint.
   *
   * @param tenantIdentifier tenant identifier
   * @param request request parameters
   * @param httpServletRequest HTTP servlet request
   * @return authorization response
   */
  private ResponseEntity<?> handleAuthorizationRequest(
      TenantIdentifier tenantIdentifier,
      MultiValueMap<String, String> request,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthRequestResponse response =
        oAuthFlowApi.request(tenantIdentifier, params, requestAttributes);

    switch (response.status()) {
      case OK, OK_SESSION_ENABLE, OK_ACCOUNT_CREATION -> {
        String url = response.frontUrl();
        HttpHeaders headers = createSecurityHeaders();
        headers.add(HttpHeaders.LOCATION, url);

        return new ResponseEntity<>(headers, HttpStatus.FOUND);
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        HttpHeaders httpHeaders = createSecurityHeaders();
        httpHeaders.add(HttpHeaders.LOCATION, response.redirectUri());

        return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
      }
      default -> {
        HttpHeaders httpHeaders = createSecurityHeaders();
        httpHeaders.add(HttpHeaders.LOCATION, response.frontUrl());

        return new ResponseEntity<>(httpHeaders, HttpStatus.FOUND);
      }
    }
  }

  @GetMapping("/{id}/view-data")
  public ResponseEntity<?> getViewData(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthViewDataResponse viewDataResponse =
        oAuthFlowApi.getViewData(
            tenantIdentifier, authorizationRequestIdentifier, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
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

    HttpHeaders headers = createSecurityHeaders();
    headers.setCacheControl("no-store, private");
    headers.setContentType(MediaType.APPLICATION_JSON);

    switch (requestResponse.status()) {
      case REDIRECABLE_OK, REDIRECABLE_BAD_REQUEST -> {
        return new ResponseEntity<>(requestResponse.contents(), headers, HttpStatus.OK);
      }
      default -> {
        return new ResponseEntity<>(
            Map.of("error", "unexpected error is occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/federations/{federation-type}/callback")
  public ResponseEntity<?> callbackFederation(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("federation-type") FederationType federationType,
      @RequestBody(required = false) MultiValueMap<String, String> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    Map<String, String[]> params = transform(body);
    FederationCallbackRequest federationCallbackRequest = new FederationCallbackRequest(params);

    FederationInteractionResult result =
        oAuthFlowApi.callbackFederation(
            tenantIdentifier,
            federationType,
            federationCallbackRequest.ssoProvider(),
            federationCallbackRequest,
            requestAttributes);

    HttpHeaders headers = createSecurityHeaders();
    headers.setCacheControl("no-store, private");
    headers.setContentType(MediaType.APPLICATION_JSON);

    return new ResponseEntity<>(
        result.response(), headers, HttpStatus.valueOf(result.statusCode()));
  }

  @PostMapping("/{id}/{interaction-type}")
  public ResponseEntity<?> interact(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      @PathVariable("interaction-type") AuthenticationInteractionType authenticationInteractionType,
      @RequestBody(required = false) Map<String, Object> params,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationInteractionRequestResult result =
        oAuthFlowApi.interact(
            tenantIdentifier,
            authorizationRequestIdentifier,
            authenticationInteractionType,
            new AuthenticationInteractionRequest(params),
            requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);

    return new ResponseEntity<>(
        result.response(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }

  @PostMapping("/{id}/authorize-with-session")
  public ResponseEntity<?> authorizeWithSession(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowApi.authorizeWithSession(
            tenantIdentifier, authorizationRequestIdentifier, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);

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
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowApi.authorize(tenantIdentifier, authorizationRequestIdentifier, requestAttributes);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);

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
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthDenyResponse oAuthDenyResponse =
        oAuthFlowApi.deny(tenantIdentifier, authorizationRequestIdentifier, requestAttributes);
    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.setCacheControl("no-store, private");
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);

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
