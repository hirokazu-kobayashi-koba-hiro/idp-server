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

package org.idp.server.control_plane.management.oidc.grant;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.oidc.grant.io.GrantManagementResponse;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level grant management API.
 *
 * <p>This API provides organization-scoped grant management operations that allow organization
 * administrators to manage authorization grants within their organization boundaries.
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
 * @see org.idp.server.usecases.control_plane.organization_manager.OrgGrantManagementEntryService
 */
public interface OrgGrantManagementApi {

  /**
   * Returns required permissions for each API operation.
   *
   * @param method the API method name (findList, get, delete)
   * @return required permissions for the operation
   * @throws UnSupportedException if the method is not supported
   */
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.GRANT_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.GRANT_READ)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.GRANT_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Finds a list of grants within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param queries query parameters for filtering and pagination
   * @param requestAttributes additional request attributes
   * @return grant management response with grant list
   */
  GrantManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationGrantedQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific grant within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param grantIdentifier the grant to retrieve
   * @param requestAttributes additional request attributes
   * @return grant management response with grant details
   */
  GrantManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationGrantedIdentifier grantIdentifier,
      RequestAttributes requestAttributes);

  /**
   * Revokes (deletes) a grant within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param grantIdentifier the grant to revoke
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without revoking the grant
   * @return grant management response with revocation result
   */
  GrantManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationGrantedIdentifier grantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
