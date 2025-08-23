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
import org.idp.server.core.openid.identity.permission.Permissions;

public class RolePermissionVerifier {

  RoleRequest roleRequest;
  Permissions permissions;

  public RolePermissionVerifier(RoleRequest roleRequest, Permissions permissions) {
    this.roleRequest = roleRequest;
    this.permissions = permissions;
  }

  public VerificationResult verify() {

    Permissions filtered = permissions.filter(roleRequest.permissions());

    if (!filtered.exists()) {
      Permissions noneMatch = permissions.filterNoneMatch(roleRequest.permissions());

      List<String> errors = new ArrayList<>();
      errors.add(
          String.format("Permission does not contains. (%s)", noneMatch.permissionNamesAsString()));
      return VerificationResult.failure(errors);
    }

    return VerificationResult.success();
  }
}
