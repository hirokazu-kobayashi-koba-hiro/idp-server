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

package org.idp.server.control_plane.management.oidc.clientinstance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceRegistrationRequest;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueries;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level management API for Client Instances.
 *
 * <p>The system-level {@link ClientInstanceManagementApi} accepts only tokens issued by the admin
 * tenant, so without this an organization administrator could not see or revoke the devices of the
 * users in their own tenants. The operations are the same; the organization access check is what
 * this adds.
 *
 * <p>The permissions are the same ones {@link ClientInstanceManagementApi} requires: an instance
 * holds the same thing whichever path the request arrived through (Issue #1898 is what happens when
 * the two drift).
 *
 * @see ClientInstanceManagementApi
 */
public interface OrgClientInstanceManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_READ)));
    map.put("revoke", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_REVOKE)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  ClientInstanceManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /** Searches the instances of every client of the tenant; {@code client_id} is a condition. */
  ClientInstanceManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceQueries queries,
      RequestAttributes requestAttributes);

  ClientInstanceManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes);

  /**
   * Stops trusting an instance, keeping its record. There is no operation that undoes it.
   *
   * @see ClientInstanceManagementApi#revoke
   */
  ClientInstanceManagementResponse revoke(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);

  ClientInstanceManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
