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

package org.idp.server.control_plane.management.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.*;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface OrgTenantManagementApi {
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_READ)));
    map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  TenantManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  TenantManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes);

  TenantManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
