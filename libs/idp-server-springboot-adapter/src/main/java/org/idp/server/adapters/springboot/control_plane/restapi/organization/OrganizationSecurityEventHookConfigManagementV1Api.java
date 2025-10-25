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
import org.idp.server.control_plane.management.security.hook.OrgSecurityEventHookConfigManagementApi;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level security event hook configuration management API controller.
 *
 * <p>This controller handles security event hook configuration management operations within an
 * organization context. It provides CRUD operations for security event hook configurations
 * belonging to a specific organization and tenant, with proper authentication and authorization
 * through the OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - POST
 * /organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configurations - Create
 * security event hook config - GET
 * /organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configurations - List
 * security event hook configs - GET
 * /organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configurations/{configId}
 * - Get specific config - PUT
 * /organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configurations/{configId}
 * - Update config - DELETE
 * /organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configurations/{configId}
 * - Delete config
 *
 * @see OrgSecurityEventHookConfigManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping(
    "/v1/management/organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configurations")
public class OrganizationSecurityEventHookConfigManagementV1Api implements ParameterTransformable {

  OrgSecurityEventHookConfigManagementApi orgSecurityEventHookConfigManagementApi;

  public OrganizationSecurityEventHookConfigManagementV1Api(
      IdpServerApplication idpServerApplication) {
    this.orgSecurityEventHookConfigManagementApi =
        idpServerApplication.orgSecurityEventHookConfigManagementApi();
  }

  /**
   * Creates a new security event hook configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param body the security event hook configuration request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the security event hook configuration creation response
   */
  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookConfigManagementResponse response =
        orgSecurityEventHookConfigManagementApi.create(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            new SecurityEventHookRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Lists security event hook configurations within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param limit the maximum number of results to return
   * @param offset the offset for pagination
   * @param httpServletRequest the HTTP request
   * @return the security event hook configuration list response
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
      @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookConfigManagementResponse response =
        orgSecurityEventHookConfigManagementApi.findList(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            limit,
            offset,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific security event hook configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the security event hook configuration identifier
   * @param httpServletRequest the HTTP request
   * @return the security event hook configuration details response
   */
  @GetMapping("/{configId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("configId") SecurityEventHookConfigurationIdentifier configId,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookConfigManagementResponse response =
        orgSecurityEventHookConfigManagementApi.get(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            configId,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates a specific security event hook configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the security event hook configuration identifier
   * @param body the security event hook configuration update request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the security event hook configuration update response
   */
  @PutMapping("/{configId}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("configId") SecurityEventHookConfigurationIdentifier configId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookConfigManagementResponse response =
        orgSecurityEventHookConfigManagementApi.update(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            configId,
            new SecurityEventHookRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Deletes a specific security event hook configuration within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param configId the security event hook configuration identifier
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the security event hook configuration deletion response
   */
  @DeleteMapping("/{configId}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable("configId") SecurityEventHookConfigurationIdentifier configId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    SecurityEventHookConfigManagementResponse response =
        orgSecurityEventHookConfigManagementApi.delete(
            organizationOperatorPrincipal.authenticationContext(),
            new TenantIdentifier(tenantId),
            configId,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
