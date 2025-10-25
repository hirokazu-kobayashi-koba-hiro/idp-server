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

package org.idp.server.control_plane.management.authentication.policy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface AuthenticationPolicyConfigurationManagementApi {

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

  AuthenticationPolicyConfigManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  AuthenticationPolicyConfigManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  AuthenticationPolicyConfigManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  AuthenticationPolicyConfigManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigurationIdentifier identifier,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  AuthenticationPolicyConfigManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
