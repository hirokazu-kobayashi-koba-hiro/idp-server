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
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface UserManagementApi {

  default AdminPermissions getRequiredPermissions(String method, Tenant tenant) {
    Map<String, AdminPermissions> map = new HashMap<>();

    // Determine permission type based on tenant type
    if (tenant.isOrganizer() || tenant.isAdmin()) {
      // ADMIN/ORGANIZER tenants require ADMIN_USER_* permissions
      map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_CREATE)));
      map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_READ)));
      map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_READ)));
      map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put("patch", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updatePassword", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updateRoles", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updatePermissions",
          new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updateTenantAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put(
          "updateOrganizationAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_UPDATE)));
      map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.ADMIN_USER_DELETE)));
      map.put("findSessions", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_READ)));
      map.put("deleteSession", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_DELETE)));
    } else {
      // PUBLIC tenants require USER_* permissions
      map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.USER_CREATE)));
      map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.USER_READ)));
      map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.USER_READ)));
      map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("patch", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("updatePassword", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("updateRoles", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put(
          "updatePermissions", new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put(
          "updateTenantAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put(
          "updateOrganizationAssignments",
          new AdminPermissions(Set.of(DefaultAdminPermission.USER_UPDATE)));
      map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.USER_DELETE)));
      map.put("findSessions", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_READ)));
      map.put("deleteSession", new AdminPermissions(Set.of(DefaultAdminPermission.SESSION_DELETE)));
    }

    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  UserManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserQueries queries,
      RequestAttributes requestAttributes);

  UserManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  UserManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse patch(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse updatePassword(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse updateRoles(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse updateTenantAssignments(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse updateOrganizationAssignments(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  UserManagementResponse findSessions(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  UserManagementResponse deleteSession(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      OPSessionIdentifier sessionIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
