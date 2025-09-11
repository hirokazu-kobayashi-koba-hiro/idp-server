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
import org.idp.server.control_plane.base.definition.OrganizationAdminPermission;
import org.idp.server.control_plane.base.definition.OrganizationAdminPermissions;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface OrgTenantManagementApi {
  default OrganizationAdminPermissions getRequiredPermissions(String method) {
    Map<String, OrganizationAdminPermissions> map = new HashMap<>();
    map.put(
        "create",
        new OrganizationAdminPermissions(Set.of(OrganizationAdminPermission.ORG_TENANT_CREATE)));
    map.put(
        "findList",
        new OrganizationAdminPermissions(Set.of(OrganizationAdminPermission.ORG_TENANT_READ)));
    map.put(
        "get",
        new OrganizationAdminPermissions(Set.of(OrganizationAdminPermission.ORG_TENANT_READ)));
    map.put(
        "update",
        new OrganizationAdminPermissions(Set.of(OrganizationAdminPermission.ORG_TENANT_UPDATE)));
    map.put(
        "delete",
        new OrganizationAdminPermissions(Set.of(OrganizationAdminPermission.ORG_TENANT_DELETE)));
    OrganizationAdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  TenantManagementResponse create(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantManagementResponse findList(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  TenantManagementResponse get(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes);

  TenantManagementResponse update(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantManagementResponse delete(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
