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
import org.idp.server.control_plane.management.permission.OrgPermissionManagementApi;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;
import org.idp.server.core.openid.identity.permission.PermissionQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level permission management REST API controller.
 *
 * <p>This controller provides organization-scoped permission management endpoints for organization
 * administrators. It handles HTTP requests and delegates to the organization permission management
 * entry service.
 *
 * <p>Supported operations:
 *
 * <ul>
 *   <li>POST - Create a new permission within an organization
 *   <li>GET - List permissions within an organization (with pagination and filtering)
 *   <li>GET /{id} - Retrieve a specific permission within an organization
 *   <li>PUT /{id} - Update a permission within an organization (with dry-run support)
 *   <li>DELETE /{id} - Delete a permission within an organization (with dry-run support)
 * </ul>
 *
 * @see OrgPermissionManagementApi
 */
@RestController
@RequestMapping("/v1/management/organizations/{organization-id}/tenants/{tenant-id}/permissions")
public class OrganizationPermissionManagementV1Api implements ParameterTransformable {

  OrgPermissionManagementApi orgPermissionManagementApi;

  public OrganizationPermissionManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgPermissionManagementApi = idpServerApplication.orgPermissionManagementApi();
  }

  /**
   * Creates a new permission within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param body the permission creation request body
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return created permission details or preview
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

    PermissionManagementResponse response =
        orgPermissionManagementApi.create(
            organizationOperatorPrincipal.authenticationContext(),
            tenantId,
            new PermissionRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Lists permissions within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param queryParams query parameters for filtering and pagination
   * @param httpServletRequest the HTTP request
   * @return list of permissions with pagination information
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestParam Map<String, String> queryParams,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    PermissionManagementResponse response =
        orgPermissionManagementApi.findList(
            organizationOperatorPrincipal.authenticationContext(),
            tenantId,
            new PermissionQueries(queryParams),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Retrieves a specific permission within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param identifier the permission identifier
   * @param httpServletRequest the HTTP request
   * @return permission details
   */
  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") PermissionIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    PermissionManagementResponse response =
        orgPermissionManagementApi.get(
            organizationOperatorPrincipal.authenticationContext(),
            tenantId,
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates a permission within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param identifier the permission identifier
   * @param body the permission update request body
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return updated permission details or preview
   */
  @PutMapping("/{id}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") PermissionIdentifier identifier,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    PermissionManagementResponse response =
        orgPermissionManagementApi.update(
            organizationOperatorPrincipal.authenticationContext(),
            tenantId,
            identifier,
            new PermissionRequest(body),
            requestAttributes,
            dryRun);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Deletes a permission within an organization.
   *
   * @param organizationOperatorPrincipal the authenticated operator
   * @param organizationId the organization identifier
   * @param tenantId the tenant identifier
   * @param identifier the permission identifier
   * @param dryRun whether to perform a dry run (preview only)
   * @param httpServletRequest the HTTP request
   * @return deletion confirmation or preview
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable("organization-id") OrganizationIdentifier organizationId,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("id") PermissionIdentifier identifier,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    PermissionManagementResponse response =
        orgPermissionManagementApi.delete(
            organizationOperatorPrincipal.authenticationContext(),
            tenantId,
            identifier,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
