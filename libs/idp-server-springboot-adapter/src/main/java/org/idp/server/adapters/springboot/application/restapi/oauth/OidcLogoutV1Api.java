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
import org.idp.server.core.oidc.io.OAuthLogoutResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/logout")
public class OidcLogoutV1Api implements ParameterTransformable {

  OAuthFlowApi oAuthFlowApi;

  public OidcLogoutV1Api(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
  }

  @GetMapping
  public ResponseEntity<?> logout(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      HttpServletRequest httpServletRequest) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthLogoutResponse response = oAuthFlowApi.logout(tenantId, params, requestAttributes);

    switch (response.status()) {
      case OK -> {
        return new ResponseEntity<>(HttpStatus.OK);
      }
      case REDIRECABLE_FOUND -> {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, response.redirectUriValue());
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
      }
      case BAD_REQUEST -> {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      }
      default -> {
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }
  }
}
