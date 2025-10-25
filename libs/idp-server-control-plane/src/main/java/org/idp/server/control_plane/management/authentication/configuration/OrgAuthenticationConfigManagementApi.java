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

package org.idp.server.control_plane.management.authentication.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigRequest;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication policy configuration management API.
 *
 * <p>This interface defines operations for managing authentication policy configurations within an
 * organization context. It provides CRUD operations for authentication policy configurations with
 * organization-level access control.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_POLICY_CONFIG_* permissions
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes.
 *
 * @see AuthenticationConfigurationManagementApi
 * @see OrganizationAccessVerifier
 */
public interface OrgAuthenticationConfigManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "create",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_POLICY_CONFIG_CREATE)));
    map.put(
        "findList",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_POLICY_CONFIG_READ)));
    map.put(
        "get",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_POLICY_CONFIG_READ)));
    map.put(
        "update",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_POLICY_CONFIG_UPDATE)));
    map.put(
        "delete",
        new AdminPermissions(Set.of(DefaultAdminPermission.AUTHENTICATION_POLICY_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  AuthenticationConfigManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  AuthenticationConfigManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  AuthenticationConfigManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
