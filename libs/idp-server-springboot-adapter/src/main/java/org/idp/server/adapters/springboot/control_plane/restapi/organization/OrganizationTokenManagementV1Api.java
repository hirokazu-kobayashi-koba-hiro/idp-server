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

package org.idp.server.adapters.springboot.control_plane.restapi.organization;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.control_plane.management.token.OrgTokenManagementApi;
import org.idp.server.control_plane.management.token.io.TokenManagementResponse;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.OAuthTokenQueries;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/organizations/{organizationId}/tenants/{tenantId}")
public class OrganizationTokenManagementV1Api implements ParameterTransformable {

  OrgTokenManagementApi orgTokenManagementApi;

  public OrganizationTokenManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgTokenManagementApi = idpServerApplication.orgTokenManagementApi();
  }

  @PostMapping("/tokens")
  public ResponseEntity<?> create(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestBody Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenManagementResponse response =
        orgTokenManagementApi.create(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            body,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/tokens")
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenManagementResponse response =
        orgTokenManagementApi.findList(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new OAuthTokenQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/tokens/{tokenId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String tokenId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenManagementResponse response =
        orgTokenManagementApi.get(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new OAuthTokenIdentifier(tokenId),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping("/tokens/{tokenId}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String tokenId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenManagementResponse response =
        orgTokenManagementApi.delete(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new OAuthTokenIdentifier(tokenId),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping("/users/{userId}/tokens")
  public ResponseEntity<?> deleteUserTokens(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String userId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TokenManagementResponse response =
        orgTokenManagementApi.deleteUserTokens(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            userId,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
