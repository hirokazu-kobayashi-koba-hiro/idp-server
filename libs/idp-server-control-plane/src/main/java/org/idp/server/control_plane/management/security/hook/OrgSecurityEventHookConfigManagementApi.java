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

package org.idp.server.control_plane.management.security.hook;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level security event hook configuration management API.
 *
 * <p>This interface defines operations for managing security event hook configurations within an
 * organization context. It provides CRUD operations for security event hook configurations with
 * organization-level access control.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       SECURITY_EVENT_HOOK_CONFIG_* permissions
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes.
 *
 * @see SecurityEventHookConfigurationManagementApi
 * @see OrganizationAccessVerifier
 */
public interface OrgSecurityEventHookConfigManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "create",
        new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_CONFIG_CREATE)));
    map.put(
        "findList",
        new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_CONFIG_READ)));
    map.put(
        "get",
        new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_CONFIG_READ)));
    map.put(
        "update",
        new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_CONFIG_UPDATE)));
    map.put(
        "delete",
        new AdminPermissions(Set.of(DefaultAdminPermission.SECURITY_EVENT_HOOK_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * Creates a new security event hook configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param request the security event hook configuration request
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run (validation only)
   * @return the security event hook configuration creation response
   */
  SecurityEventHookConfigManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Lists security event hook configurations within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param limit the maximum number of results to return
   * @param offset the offset for pagination
   * @param requestAttributes the request attributes
   * @return the security event hook configuration list response
   */
  SecurityEventHookConfigManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  /**
   * Gets a specific security event hook configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param identifier the security event hook configuration identifier
   * @param requestAttributes the request attributes
   * @return the security event hook configuration details response
   */
  SecurityEventHookConfigManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  /**
   * Updates a specific security event hook configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param identifier the security event hook configuration identifier
   * @param request the security event hook configuration update request
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run (validation only)
   * @return the security event hook configuration update response
   */
  SecurityEventHookConfigManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * Deletes a specific security event hook configuration within the organization.
   *
   * @param organizationIdentifier the organization identifier
   * @param tenantIdentifier the tenant identifier
   * @param operator the operator user
   * @param oAuthToken the OAuth token
   * @param identifier the security event hook configuration identifier
   * @param requestAttributes the request attributes
   * @param dryRun whether to perform a dry run (validation only)
   * @return the security event hook configuration deletion response
   */
  SecurityEventHookConfigManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
