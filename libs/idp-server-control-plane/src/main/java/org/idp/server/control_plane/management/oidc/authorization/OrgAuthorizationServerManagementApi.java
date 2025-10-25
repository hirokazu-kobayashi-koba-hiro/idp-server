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

package org.idp.server.control_plane.management.oidc.authorization;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authorization server management API interface.
 *
 * <p>This interface defines operations for managing authorization server configurations within
 * organization boundaries. It provides organization-scoped access to authorization server
 * configuration retrieval and updates.
 *
 * <p>Unlike tenant-level operations, organization-level operations require:
 *
 * <ol>
 *   <li>Organization membership verification
 *   <li>Tenant access verification within the organization
 *   <li>Organization-tenant relationship verification
 *   <li>Required permissions verification
 * </ol>
 *
 * @see AuthorizationServerManagementApi
 * @see OrganizationAccessVerifier
 */
public interface OrgAuthorizationServerManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_READ)));
    map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.TENANT_UPDATE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  AuthorizationServerManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes);

  AuthorizationServerManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationServerUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
