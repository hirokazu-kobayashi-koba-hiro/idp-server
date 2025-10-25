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

package org.idp.server.control_plane.management.permission.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.platform.json.JsonNodeWrapper;

public class PermissionRegistrationVerifier {

  private final PermissionRequest request;
  private final Permissions existingPermissions;

  public PermissionRegistrationVerifier(
      PermissionRequest request, Permissions existingPermissions) {
    this.request = request;
    this.existingPermissions = existingPermissions;
  }

  public void verify() {
    JsonNodeWrapper requestJson = JsonNodeWrapper.fromMap(request.toMap());
    String name = requestJson.getValueOrEmptyAsString("name");

    List<String> errors = new ArrayList<>();

    // Check if permission with same name already exists
    Permissions filtered = existingPermissions.filterByName(List.of(name));
    if (filtered.exists()) {
      errors.add("Permission '" + name + "' already exists");
    }

    if (!errors.isEmpty()) {
      throw new InvalidRequestException("permission registration verification failed", errors);
    }
  }
}
