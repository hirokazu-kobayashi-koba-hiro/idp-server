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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.Roles;

public enum AdminRole {
  ADMINISTRATOR(
      "c6df7aaa-ab0f-4c31-839d-49b6874de144",
      "administrator",
      "administrator has all permissions",
      AdminPermission.getAll()),
  EDITOR(
      "46a97eba-feb5-47a0-9b29-d17dca2e5b00",
      "editor",
      "editor has permissions for edition",
      createEditorPermissions()),
  VIEWER(
      "c393adc2-f58b-47d5-b351-24b9615c8dc0",
      "viewer",
      "viewer has permissions for view",
      AdminPermission.findReadPermissions());

  private final String id;
  private final String name;
  private final String description;
  private final Set<AdminPermission> permissions;

  AdminRole(String id, String name, String description, Set<AdminPermission> permissions) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.permissions = permissions;
  }

  public Role toRole() {
    List<Permission> permissions =
        this.permissions.stream().map(AdminPermission::toPermission).toList();
    return new Role(id, name, description, permissions);
  }

  public static Roles toRoles() {
    List<Role> roles = new ArrayList<>();

    for (AdminRole adminRole : values()) {
      roles.add(adminRole.toRole());
    }

    return new Roles(roles);
  }

  public Set<AdminPermission> permissions() {
    return permissions;
  }

  public boolean hasPermission(AdminPermission permission) {
    return permissions.contains(permission);
  }

  private static Set<AdminPermission> createEditorPermissions() {
    Set<AdminPermission> editorPermissions = new HashSet<>();
    Set<AdminPermission> readPermissions = AdminPermission.findReadPermissions();
    Set<AdminPermission> updatePermissions = AdminPermission.findUpdatePermissions();
    editorPermissions.addAll(readPermissions);
    editorPermissions.addAll(updatePermissions);
    return editorPermissions;
  }
}
