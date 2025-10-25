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
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.control_plane.management.oidc.client.OrgClientManagementApi;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/organizations/{organizationId}/tenants/{tenantId}/clients")
public class OrganizationClientManagementV1Api implements ParameterTransformable {

  OrgClientManagementApi orgClientManagementApi;

  public OrganizationClientManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgClientManagementApi = idpServerApplication.orgClientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientManagementResponse response =
        orgClientManagementApi.create(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new ClientRegistrationRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    ClientQueries queries = new ClientQueries(queryParams);

    ClientManagementResponse response =
        orgClientManagementApi.findList(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            queries,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{clientId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String clientId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientManagementResponse response =
        orgClientManagementApi.get(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new ClientIdentifier(clientId),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PutMapping("/{clientId}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String clientId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    ClientIdentifier identifier = new ClientIdentifier(clientId);

    ClientManagementResponse response =
        orgClientManagementApi.update(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            identifier,
            new ClientRegistrationRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping("/{clientId}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String clientId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    ClientManagementResponse response =
        orgClientManagementApi.delete(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new ClientIdentifier(clientId),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
