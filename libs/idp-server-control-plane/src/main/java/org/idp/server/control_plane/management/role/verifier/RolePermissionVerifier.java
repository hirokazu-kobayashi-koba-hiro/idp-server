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

package org.idp.server.control_plane.management.role.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Roles;

public class RolePermissionVerifier {

  RoleRequest roleRequest;
  Roles roles;
  Permissions permissions;
  String updatingRoleId;

  public RolePermissionVerifier(
      RoleRequest roleRequest, Roles roles, Permissions permissions, String updatingRoleId) {
    this.roleRequest = roleRequest;
    this.roles = roles;
    this.permissions = permissions;
    this.updatingRoleId = updatingRoleId;
  }

  public VerificationResult verify() {

    if (roles.containsByName(roleRequest.name())) {
      if (!isSelfUpdate()) {
        List<String> errors = new ArrayList<>();
        errors.add(String.format("Role is already exists: %s", roleRequest.name()));
        return VerificationResult.failure(errors);
      }
    }

    Permissions filtered = permissions.filterById(roleRequest.permissions());

    if (filtered.size() != roleRequest.permissions().size()) {
      List<String> existingIds = filtered.toList().stream().map(Permission::id).toList();
      List<String> nonExistentIds =
          roleRequest.permissions().stream().filter(id -> !existingIds.contains(id)).toList();

      List<String> errors = new ArrayList<>();
      errors.add(
          String.format("Permission does not exists: %s", String.join(", ", nonExistentIds)));
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }

  private boolean isSelfUpdate() {
    if (updatingRoleId == null) {
      return false;
    }
    return roles
        .getByName(roleRequest.name())
        .map(role -> role.id())
        .map(id -> id.equals(updatingRoleId))
        .orElse(false);
  }
}
