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
import org.idp.server.control_plane.management.federation.OrgFederationConfigManagementApi;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level federation configuration management API controller.
 *
 * <p>This controller handles federation configuration management operations within an organization
 * context. It provides CRUD operations for federation configurations belonging to a specific
 * organization and tenant, with proper authentication and authorization through the
 * OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - POST /organizations/{organizationId}/tenants/{tenantId}/federation-configs -
 * Create federation config - GET
 * /organizations/{organizationId}/tenants/{tenantId}/federation-configs - List federation configs -
 * GET /organizations/{organizationId}/tenants/{tenantId}/federation-configs/{configId} - Get
 * specific config - PUT
 * /organizations/{organizationId}/tenants/{tenantId}/federation-configs/{configId} - Update config
 * - DELETE /organizations/{organizationId}/tenants/{tenantId}/federation-configs/{configId} -
 * Delete config
 *
 * @see OrgFederationConfigManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenantId}/federation-configs")
public class OrganizationFederationConfigManagementV1Api implements ParameterTransformable {

  OrgFederationConfigManagementApi orgFederationConfigManagementApi;

  public OrganizationFederationConfigManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgFederationConfigManagementApi = idpServerApplication.orgFederationConfigManagementApi();
  }

  /**
   * Creates a new federation configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param body the federation configuration request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the federation configuration creation response
   */
  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    FederationConfigManagementResponse response =
        orgFederationConfigManagementApi.create(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new FederationConfigRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Lists federation configurations within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param queryParams the query parameters for filtering
   * @param httpServletRequest the HTTP request
   * @return the federation configuration list response
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    FederationConfigManagementResponse response =
        orgFederationConfigManagementApi.findList(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new FederationQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific federation configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the federation configuration identifier
   * @param httpServletRequest the HTTP request
   * @return the federation configuration details response
   */
  @GetMapping("/{configId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("configId") FederationConfigurationIdentifier configId,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    FederationConfigManagementResponse response =
        orgFederationConfigManagementApi.get(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            configId,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates a specific federation configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the federation configuration identifier
   * @param body the federation configuration update request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the federation configuration update response
   */
  @PutMapping("/{configId}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("configId") FederationConfigurationIdentifier configId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    FederationConfigManagementResponse response =
        orgFederationConfigManagementApi.update(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            configId,
            new FederationConfigRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Deletes a specific federation configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the federation configuration identifier
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the federation configuration deletion response
   */
  @DeleteMapping("/{configId}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("configId") FederationConfigurationIdentifier configId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    FederationConfigManagementResponse response =
        orgFederationConfigManagementApi.delete(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            configId,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
