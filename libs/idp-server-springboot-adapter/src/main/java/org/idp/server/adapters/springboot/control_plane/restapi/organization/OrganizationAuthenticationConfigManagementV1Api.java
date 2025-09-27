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
import org.idp.server.control_plane.management.authentication.configuration.OrgAuthenticationConfigManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigRequest;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level authentication configuration management API controller.
 *
 * <p>This controller handles authentication configuration management operations within an
 * organization context. It provides CRUD operations for authentication configurations belonging to
 * a specific organization and tenant, with proper authentication and authorization through the
 * OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - POST
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-configurations - Create
 * authentication config - GET
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-configurations - List
 * authentication configs - GET
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-configurations/{configId} - Get
 * specific config - PUT
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-configurations/{configId} -
 * Update config - DELETE
 * /organizations/{organizationId}/tenants/{tenantId}/authentication-configurations/{configId} -
 * Delete config
 *
 * @see OrgAuthenticationConfigManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-configurations")
public class OrganizationAuthenticationConfigManagementV1Api implements ParameterTransformable {

  OrgAuthenticationConfigManagementApi orgAuthenticationConfigManagementApi;

  public OrganizationAuthenticationConfigManagementV1Api(
      IdpServerApplication idpServerApplication) {
    this.orgAuthenticationConfigManagementApi =
        idpServerApplication.orgAuthenticationConfigManagementApi();
  }

  /**
   * Creates a new authentication configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param body the authentication configuration request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the authentication configuration creation response
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

    AuthenticationConfigManagementResponse response =
        orgAuthenticationConfigManagementApi.create(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuthenticationConfigRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Lists all authentication configurations belonging to the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param limitValue the maximum number of results to return
   * @param offsetValue the offset for pagination
   * @param httpServletRequest the HTTP request
   * @return the authentication configuration list response
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    int limit = Integer.parseInt(limitValue);
    int offset = Integer.parseInt(offsetValue);

    AuthenticationConfigManagementResponse response =
        orgAuthenticationConfigManagementApi.findList(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            limit,
            offset,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific authentication configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the authentication configuration identifier from path
   * @param httpServletRequest the HTTP request
   * @return the authentication configuration details response
   */
  @GetMapping("/{configId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String configId,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationConfigManagementResponse response =
        orgAuthenticationConfigManagementApi.get(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuthenticationConfigurationIdentifier(configId),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates a specific authentication configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the authentication configuration identifier from path
   * @param body the authentication configuration update request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the authentication configuration update response
   */
  @PutMapping("/{configId}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String configId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationConfigManagementResponse response =
        orgAuthenticationConfigManagementApi.update(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuthenticationConfigurationIdentifier(configId),
            new AuthenticationConfigRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Deletes a specific authentication configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the authentication configuration identifier from path
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the authentication configuration deletion response
   */
  @DeleteMapping("/{configId}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String configId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    AuthenticationConfigManagementResponse response =
        orgAuthenticationConfigManagementApi.delete(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new AuthenticationConfigurationIdentifier(configId),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
