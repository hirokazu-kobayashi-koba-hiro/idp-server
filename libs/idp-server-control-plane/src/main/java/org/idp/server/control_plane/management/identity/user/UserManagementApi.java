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

package org.idp.server.control_plane.management.identity.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.UserQueries;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface UserManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(AdminPermission.USER_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(AdminPermission.USER_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.USER_READ)));
    map.put("update", new AdminPermissions(Set.of(AdminPermission.USER_UPDATE)));
    map.put("patch", new AdminPermissions(Set.of(AdminPermission.USER_UPDATE)));
    map.put("updatePassword", new AdminPermissions(Set.of(AdminPermission.USER_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(AdminPermission.USER_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  UserManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserQueries queries,
      RequestAttributes requestAttributes);

  UserManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  UserManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse patch(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse updatePassword(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
