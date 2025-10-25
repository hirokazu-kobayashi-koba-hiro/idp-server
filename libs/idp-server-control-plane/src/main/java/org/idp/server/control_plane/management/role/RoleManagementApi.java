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

package org.idp.server.control_plane.management.role;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface RoleManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_READ)));
    map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_UPDATE)));
    map.put("removePermissions", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  RoleManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  RoleManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleQueries queries,
      RequestAttributes requestAttributes);

  RoleManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RequestAttributes requestAttributes);

  RoleManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  RoleManagementResponse removePermissions(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  RoleManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RoleIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
