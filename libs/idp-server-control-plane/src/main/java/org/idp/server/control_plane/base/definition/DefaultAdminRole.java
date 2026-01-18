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

package org.idp.server.control_plane.base.definition;

import java.util.*;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;

public enum DefaultAdminRole {
  ADMINISTRATOR(
      "administrator",
      "Administrator with full control plane access via wildcard permission",
      DefaultAdminPermission.getWildcard());

  private final String name;
  private final String description;
  private final Set<DefaultAdminPermission> permissions;

  DefaultAdminRole(String name, String description, Set<DefaultAdminPermission> permissions) {
    this.name = name;
    this.description = description;
    this.permissions = permissions;
  }

  public static Roles create(Permissions allPermissions) {
    List<Role> roles = new ArrayList<>();

    for (DefaultAdminRole defaultAdminRole : values()) {
      Set<DefaultAdminPermission> rolePermissions = defaultAdminRole.permissions();
      String id = UUID.randomUUID().toString();
      String name = defaultAdminRole.name;
      String description = defaultAdminRole.description;
      List<Permission> permissions = convertToPermissions(allPermissions, rolePermissions);
      Role role = new Role(id, name, description, permissions);
      roles.add(role);
    }

    return new Roles(roles);
  }

  private static List<Permission> convertToPermissions(
      Permissions allPermissions, Set<DefaultAdminPermission> defaultRolePermissions) {
    Permissions filtered =
        allPermissions.filterByName(
            defaultRolePermissions.stream().map(DefaultAdminPermission::value).toList());
    return filtered.toList();
  }

  public Set<DefaultAdminPermission> permissions() {
    return permissions;
  }
}
