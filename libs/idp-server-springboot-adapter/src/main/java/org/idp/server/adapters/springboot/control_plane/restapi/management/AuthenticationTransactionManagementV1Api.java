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

package org.idp.server.adapters.springboot.control_plane.restapi.management;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.core.extension.ciba.CibaFlowApi;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.identity.UserOperationApi;
import org.idp.server.core.openid.oauth.OAuthFlowApi;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(("/v1/management/tenants/{tenant-id}/authentication-transactions"))
public class AuthenticationTransactionManagementV1Api implements ParameterTransformable {

  AuthenticationTransactionManagementApi authenticationTransactionManagementApi;
  OAuthFlowApi oAuthFlowApi;
  CibaFlowApi cibaFlowApi;
  UserOperationApi userOperationApi;

  public AuthenticationTransactionManagementV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationTransactionManagementApi =
        idpServerApplication.authenticationTransactionManagementApi();
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
    this.cibaFlowApi = idpServerApplication.cibaFlowApi();
    this.userOperationApi = idpServerApplication.userOperationApi();
  }

  @GetMapping
  public ResponseEntity<?> findList(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransactionManagementResponse response =
        authenticationTransactionManagementApi.findList(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            new AuthenticationTransactionQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") AuthenticationTransactionIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationTransactionManagementResponse response =
        authenticationTransactionManagementApi.get(
            operatorPrincipal.authenticationContext(),
            tenantIdentifier,
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
