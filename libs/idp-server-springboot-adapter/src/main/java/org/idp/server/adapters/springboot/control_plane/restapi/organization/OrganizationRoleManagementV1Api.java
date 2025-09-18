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
import org.idp.server.control_plane.management.role.OrgRoleManagementApi;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level role management REST API controller.
 *
 * <p>This controller provides organization-scoped role management endpoints for organization
 * administrators. It handles HTTP requests and delegates to the organization role management entry
 * service.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>POST - Create a new role within an organization
 *   <li>GET - List roles within an organization (with pagination and filtering)
 *   <li>GET /{id} - Retrieve a specific role within an organization
 *   <li>PUT /{id} - Update a role within an organization (with dry-run support)
 *   <li>DELETE /{id} - Delete a role within an organization (with dry-run support)
 * </ul>
 *
 * @see OrgRoleManagementApi
 */
@RestController
@RequestMapping("/v1/management/organizations/{organization-id}/tenants/{tenant-id}/roles")
public class OrganizationRoleManagementV1Api implements ParameterTransformable {

  OrgRoleManagementApi orgRoleManagementApi;

  public OrganizationRoleManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgRoleManagementApi = idpServerApplication.orgRoleManagementApi();
  }

  /**
   * Creates a new role within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param body the role creation request body
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return created role details or preview
   */
  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    RoleManagementResponse response =
        orgRoleManagementApi.create(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new RoleRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Lists roles within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param queryParams query parameters for filtering and pagination
   * @param httpServletRequest the HTTP request
   * @return list of roles with pagination information
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    RoleManagementResponse response =
        orgRoleManagementApi.findList(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new RoleQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Retrieves a specific role within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param identifier the role identifier
   * @param httpServletRequest the HTTP request
   * @return role details
   */
  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") RoleIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    RoleManagementResponse response =
        orgRoleManagementApi.get(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates a role within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param identifier the role identifier
   * @param body the role update request body
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return updated role details or preview
   */
  @PutMapping("/{id}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") RoleIdentifier identifier,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    RoleManagementResponse response =
        orgRoleManagementApi.update(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            identifier,
            new RoleRequest(body),
            requestAttributes,
            dryRun);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Deletes a role within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param identifier the role identifier
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return deletion confirmation or preview
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") RoleIdentifier identifier,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    RoleManagementResponse response =
        orgRoleManagementApi.delete(
            organizationId,
            tenantId,
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            identifier,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
