/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.restapi.user;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.model.ResourceOwnerPrincipal;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserOperationApi;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/users")
public class UserV1Api implements ParameterTransformable {

  UserOperationApi userOperationApi;

  public UserV1Api(IdpServerApplication idpServerApplication) {
    this.userOperationApi = idpServerApplication.userOperationApi();
  }

  @DeleteMapping
  public ResponseEntity<?> apply(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    userOperationApi.delete(tenantIdentifier, user, oAuthToken, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(Map.of(), httpHeaders, HttpStatus.valueOf(204));
  }
}
