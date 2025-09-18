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

import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleRemovePermissionContextCreator {

  Tenant tenant;
  Role before;
  RoleRequest request;
  Permissions allPermissions;
  boolean dryRun;
  JsonConverter jsonConverter;

  public RoleRemovePermissionContextCreator(
      Tenant tenant, Role before, RoleRequest request, Permissions allPermissions, boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.allPermissions = allPermissions;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public RoleRemovePermissionContext create() {
    String id = before.id();
    String name = request.name();
    String description = request.description();

    Permissions removedPermission = this.allPermissions.filterByName(request.permissions());

    Role role = new Role(id, name, description, removedPermission.toList());

    return new RoleRemovePermissionContext(
        tenant, before, request, role, removedPermission, allPermissions, dryRun);
  }
}
