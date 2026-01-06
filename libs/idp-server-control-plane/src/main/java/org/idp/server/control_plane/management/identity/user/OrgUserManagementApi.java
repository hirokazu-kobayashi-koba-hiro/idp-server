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

package org.idp.server.control_plane.management.identity.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level user management API.
 *
 * <p>This API provides organization-scoped user management operations that allow organization
 * administrators to manage users within their organization boundaries.
 *
 * <p>Organization-level operations follow the same access control pattern:
 *
 * <ol>
 *   <li><strong>Tenant access verification</strong> - Ensures the user has access to the target
 *       tenant
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       DefaultAdminPermission
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * OrgUserManagementApi api = new OrgUserManagementEntryService(...);
 *
 * UserManagementResponse response = api.create(
 *     organizationId,
 *     tenantIdentifier,
 *     operator,
 *     oAuthToken,
 *     userRequest,
 *     requestAttributes,
 *     false
 * );
 *
 * if (response.isSuccess()) {
 *     // User created successfully
 * }
 * }</pre>
 *
 * @see UserManagementApi
 * @see org.idp.server.usecases.control_plane.organization_manager.OrgUserManagementEntryService
 */
public interface OrgUserManagementApi {

  /**
   * Returns required permissions for each API operation.
   *
   * <p>Permission requirements vary based on tenant type:
   *
   * <ul>
   *   <li>ADMIN/ORGANIZER tenants: Requires ADMIN_USER_* permissions
   *   <li>PUBLIC tenants: Requires USER_* permissions
   * </ul>
   *
   * @param method the API method name (create, findList, get, update, delete)
   * @param tenant the target tenant
   * @return required permissions for the operation
   * @throws UnSupportedException if the method is not supported
   */
  default AdminPermissions getRequiredPermissions(String method, Tenant tenant) {
    Map<String, AdminPermissions> map = new HashMap<>();

    if (tenant.isOrganizer() || tenant.isAdmin()) {
      // ADMIN/ORGANIZER tenants require ADMIN_USER_* permissions
      map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_CREATE)));
      map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_READ)));
      map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_READ)));
      map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put("patch", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updatePassword", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updateRoles", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updatePermissions",
          new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updateTenantAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updateOrganizationAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_DELETE)));
      map.put("findSessions", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_READ)));
      map.put("deleteSession", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_DELETE)));
      map.put(
          "deleteSessions", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_DELETE)));
    } else {
      // PUBLIC tenants require USER_* permissions
      map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.USER_CREATE)));
      map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.USER_READ)));
      map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.USER_READ)));
      map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("patch", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("updatePassword", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("updateRoles", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put(
          "updatePermissions", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put(
          "updateTenantAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put(
          "updateOrganizationAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.USER_DELETE)));
      map.put("findSessions", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_READ)));
      map.put("deleteSession", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_DELETE)));
      map.put(
          "deleteSessions", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_DELETE)));
    }

    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Creates a new user within the organization.
   *
   * @param organizationIdentifier the organization to create the user in
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param request the user registration request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without creating the user
   * @return user management response with creation result
   */
  UserManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Finds a list of users within the organization.
   *
   * @param organizationIdentifier the organization to search in
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param queries query parameters for filtering and pagination
   * @param requestAttributes additional request attributes
   * @return user management response with user list
   */
  UserManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific user within the organization.
   *
   * @param organizationIdentifier the organization to search in
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to retrieve
   * @param requestAttributes additional request attributes
   * @return user management response with user details
   */
  UserManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  /**
   * Updates a user within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to update
   * @param request the updated user registration request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the user
   * @return user management response with update result
   */
  UserManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Deletes a user within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to delete
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without deleting the user
   * @return user management response with deletion result
   */
  UserManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Partially updates a user within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to update
   * @param request the partial update request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the user
   * @return user management response with update result
   */
  UserManagementResponse patch(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Updates a user's password within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to update
   * @param request the password update request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the password
   * @return user management response with update result
   */
  UserManagementResponse updatePassword(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Updates a user's roles within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to update
   * @param request the roles update request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the roles
   * @return user management response with update result
   */
  UserManagementResponse updateRoles(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Updates a user's tenant assignments within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to update
   * @param request the tenant assignments update request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the assignments
   * @return user management response with update result
   */
  UserManagementResponse updateTenantAssignments(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Updates a user's organization assignments within the organization.
   *
   * @param organizationIdentifier the organization containing the user
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param userIdentifier the user to update
   * @param request the organization assignments update request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the assignments
   * @return user management response with update result
   */
  UserManagementResponse updateOrganizationAssignments(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse findSessions(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  UserManagementResponse deleteSession(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      OPSessionIdentifier sessionIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse deleteSessions(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
