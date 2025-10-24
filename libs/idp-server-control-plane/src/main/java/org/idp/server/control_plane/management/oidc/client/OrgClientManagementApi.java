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

package org.idp.server.control_plane.management.oidc.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level client management API.
 *
 * <p>This API provides organization-scoped client management operations that allow organization
 * administrators to manage OAuth/OIDC clients within their organization boundaries.
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
 * OrgClientManagementApi api = new OrgClientManagementEntryService(...);
 *
 * ClientManagementResponse response = api.create(
 *     organizationId,
 *     tenantIdentifier,
 *     operator,
 *     oAuthToken,
 *     clientRequest,
 *     requestAttributes,
 *     false
 * );
 *
 * if (response.isSuccess()) {
 *     // Client created successfully
 * }
 * }</pre>
 *
 * @see ClientManagementApi
 * @see org.idp.server.usecases.control_plane.organization_manager.OrgClientManagementEntryService
 */
public interface OrgClientManagementApi {

  /**
   * Returns required permissions for each API operation.
   *
   * <p>All operations use DefaultAdminPermission.CLIENT_* permissions for consistency with
   * system-level client management.
   *
   * @param method the API method name (create, findList, get, update, delete)
   * @return required permissions for the operation
   * @throws UnSupportedException if the method is not supported
   */
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_READ)));
    map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Creates a new client within the organization.
   *
   * @param organizationIdentifier the organization to create the client in
   * @param tenantIdentifier the tenant for database access
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token for the request
   * @param request the client registration request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without creating the client
   * @return client management response with creation result
   */
  ClientManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Finds a list of clients within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param queries query parameters for filtering and pagination
   * @param requestAttributes additional request attributes
   * @return client management response with client list
   */
  ClientManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific client within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param clientIdentifier the client to retrieve
   * @param requestAttributes additional request attributes
   * @return client management response with client details
   */
  ClientManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes);

  /**
   * Updates a client within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param clientIdentifier the client to update
   * @param request the updated client registration request
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without updating the client
   * @return client management response with update result
   */
  ClientManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Deletes a client within the organization.
   *
   * @param authenticationContext the organization authentication context
   * @param tenantIdentifier the tenant for database access
   * @param clientIdentifier the client to delete
   * @param requestAttributes additional request attributes
   * @param dryRun if true, performs validation without deleting the client
   * @return client management response with deletion result
   */
  ClientManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
