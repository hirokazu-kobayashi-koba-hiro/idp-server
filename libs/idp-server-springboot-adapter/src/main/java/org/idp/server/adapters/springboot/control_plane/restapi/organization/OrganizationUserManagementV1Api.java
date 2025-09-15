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
import java.util.HashMap;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OrganizationOperatorPrincipal;
import org.idp.server.control_plane.management.identity.user.OrgUserManagementApi;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Organization-level user management API controller.
 *
 * <p>This controller handles user management operations within an organization context. It provides
 * CRUD operations for users belonging to a specific organization and tenant, with proper
 * authentication and authorization through the OrganizationOperatorPrincipal.
 *
 * <p>All operations are performed within the context of the organization's admin tenant, ensuring
 * proper isolation and access control.
 *
 * <p>API endpoints: - POST /organizations/{organizationId}/tenants/{tenantId}/users - Create a new
 * user - GET /organizations/{organizationId}/tenants/{tenantId}/users - List organization users -
 * GET /organizations/{organizationId}/tenants/{tenantId}/users/{userId} - Get specific user - PUT
 * /organizations/{organizationId}/tenants/{tenantId}/users/{userId} - Update user - DELETE
 * /organizations/{organizationId}/tenants/{tenantId}/users/{userId} - Delete user
 *
 * @see OrgUserManagementApi
 * @see OrganizationOperatorPrincipal
 */
@RestController
@RequestMapping("/v1/management/organizations/{organizationId}/tenants/{tenantId}/users")
public class OrganizationUserManagementV1Api implements ParameterTransformable {

  OrgUserManagementApi orgUserManagementApi;

  public OrganizationUserManagementV1Api(IdpServerApplication idpServerApplication) {
    this.orgUserManagementApi = idpServerApplication.orgUserManagementApi();
  }

  /**
   * Creates a new user within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param body the user creation request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the user creation response
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

    UserManagementResponse response =
        orgUserManagementApi.create(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new UserRegistrationRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Lists all users belonging to the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param limitValue the maximum number of results to return
   * @param offsetValue the offset for pagination
   * @param userId optional user ID filter
   * @param username optional username filter
   * @param httpServletRequest the HTTP request
   * @return the user list response
   */
  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue,
      @RequestParam(value = "user_id", required = false) String userId,
      @RequestParam(value = "username", required = false) String username,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("limit", limitValue);
    queryParams.put("offset", offsetValue);
    if (userId != null) {
      queryParams.put("user_id", userId);
    }
    if (username != null) {
      queryParams.put("username", username);
    }
    UserQueries queries = new UserQueries(queryParams);

    UserManagementResponse response =
        orgUserManagementApi.findList(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            queries,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Gets a specific user within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param userId the user identifier from path
   * @param httpServletRequest the HTTP request
   * @return the user details response
   */
  @GetMapping("/{userId}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String userId,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserManagementResponse response =
        orgUserManagementApi.get(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new UserIdentifier(userId),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Updates a specific user within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param userId the user identifier from path
   * @param body the user update request body
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the user update response
   */
  @PutMapping("/{userId}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String userId,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserManagementResponse response =
        orgUserManagementApi.update(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new UserIdentifier(userId),
            new UserRegistrationRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /**
   * Deletes a specific user within the organization.
   *
   * @param organizationOperatorPrincipal the authenticated organization operator
   * @param organizationId the organization identifier from path
   * @param tenantId the tenant identifier from path
   * @param userId the user identifier from path
   * @param dryRun whether to perform a dry run (validation only)
   * @param httpServletRequest the HTTP request
   * @return the user deletion response
   */
  @DeleteMapping("/{userId}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OrganizationOperatorPrincipal organizationOperatorPrincipal,
      @PathVariable String organizationId,
      @PathVariable String tenantId,
      @PathVariable String userId,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    OrganizationIdentifier organizationIdentifier =
        organizationOperatorPrincipal.getOrganizationId();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserManagementResponse response =
        orgUserManagementApi.delete(
            organizationIdentifier,
            new TenantIdentifier(tenantId),
            organizationOperatorPrincipal.getUser(),
            organizationOperatorPrincipal.getOAuthToken(),
            new UserIdentifier(userId),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
