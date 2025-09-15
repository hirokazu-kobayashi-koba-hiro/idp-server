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

package org.idp.server.control_plane.management.federation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationQueries;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level federation configuration management API.
 *
 * <p>This interface defines operations for managing federation configurations within an
 * organization context. It provides CRUD operations for federation configurations with
 * organization-level access control.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       FEDERATION_CONFIG_* permissions
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes.
 *
 * @see FederationConfigurationManagementApi
 * @see org.idp.server.control_plane.organization.access.OrganizationAccessVerifier
 */
public interface OrgFederationConfigManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "create", new AdminPermissions(Set.of(DefaultAdminPermission.FEDERATION_CONFIG_CREATE)));
    map.put(
        "findList", new AdminPermissions(Set.of(DefaultAdminPermission.FEDERATION_CONFIG_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.FEDERATION_CONFIG_READ)));
    map.put(
        "update", new AdminPermissions(Set.of(DefaultAdminPermission.FEDERATION_CONFIG_UPDATE)));
    map.put(
        "delete", new AdminPermissions(Set.of(DefaultAdminPermission.FEDERATION_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Creates a new federation configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the federation configuration request
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run (validation only)
   * @return the federation configuration creation response
   */
  FederationConfigManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Lists federation configurations within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param queries the federation queries
   * @param requestAttributes the request attributes
   * @return the federation configuration list response
   */
  FederationConfigManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationQueries queries,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific federation configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param identifier the federation configuration identifier
   * @param requestAttributes the request attributes
   * @return the federation configuration details response
   */
  FederationConfigManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  /**
   * Updates a specific federation configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param identifier the federation configuration identifier
   * @param request the federation configuration update request
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run (validation only)
   * @return the federation configuration update response
   */
  FederationConfigManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Deletes a specific federation configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param identifier the federation configuration identifier
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run (validation only)
   * @return the federation configuration deletion response
   */
  FederationConfigManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
