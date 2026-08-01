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
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceManagementResponse;
import org.idp.server.control_plane.management.oidc.clientinstance.io.ClientInstanceRegistrationRequest;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Management API for Client Instances.
 *
 * <p>A Client Instance is a registered installation of a client on a device, holding the Client
 * Instance Key trusted by {@code attest_jwt_client_auth} when {@code
 * client_attestation_trust_source = registered_instance_key}.
 *
 * <p>Instances are part of a client's configuration, so the operations reuse the client permissions
 * rather than introducing a separate permission set.
 */
public interface ClientInstanceManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_READ)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.CLIENT_INSTANCE_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  ClientInstanceManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      ClientInstanceRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  ClientInstanceManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  ClientInstanceManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes);

  ClientInstanceManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      ClientInstanceIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
