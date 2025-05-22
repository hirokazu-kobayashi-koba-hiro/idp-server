/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.restapi.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequest;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.federation.FederationType;
import org.idp.server.core.oidc.federation.io.FederationRequestResponse;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.core.oidc.io.*;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/authorizations")
public class OAuthV1Api implements ParameterTransformable {

  OAuthFlowApi oAuthFlowApi;

  public OAuthV1Api(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
  }

  @PostMapping("/push")
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

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(response.contents(), httpHeaders, HttpStatus.OK);
  }

  @GetMapping
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(required = false) MultiValueMap<String, String> request,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthRequestResponse response =
        oAuthFlowApi.request(tenantIdentifier, params, requestAttributes);

    switch (response.status()) {
      case OK, OK_SESSION_ENABLE, OK_ACCOUNT_CREATION -> {
        String url = response.frontUrl();
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
        return new ResponseEntity<>(requestResponse.contents(), headers, HttpStatus.OK);
      }
      default -> {
        return new ResponseEntity<>(
            Map.of("error", "unexpected error is occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }

  @PostMapping("/{id}/{mfa-interaction-type}")
  public ResponseEntity<?> interact(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      @PathVariable("mfa-interaction-type")
          AuthenticationInteractionType authenticationInteractionType,
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
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowApi.authorizeWithSession(
            tenantIdentifier, authorizationRequestIdentifier, requestAttributes);

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
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthAuthorizeResponse authAuthorizeResponse =
        oAuthFlowApi.authorize(tenantIdentifier, authorizationRequestIdentifier, requestAttributes);

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
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthorizationRequestIdentifier authorizationRequestIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    OAuthDenyResponse oAuthDenyResponse =
        oAuthFlowApi.deny(tenantIdentifier, authorizationRequestIdentifier, requestAttributes);
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
