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

package org.idp.server.core.adapters.datasource.identity.role.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.platform.json.JsonNodeWrapper;

public class ModelConverter {

  static Role convert(Map<String, String> result) {
    String id = result.get("id");
    String name = result.get("name");
    String description = result.get("description");
    List<Permission> permissions = convert(result.get("permissions"));
    return new Role(id, name, description, permissions);
  }

  static List<Permission> convert(String json) {
    if (json == null || json.equals("[]") || json.isEmpty()) {
      return List.of();
    }
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(json);
    List<Permission> permissions = new ArrayList<>();

    for (JsonNodeWrapper node : jsonNodeWrapper.elements()) {
      String id = node.getValueOrEmptyAsString("permission_id");
      String name = node.getValueOrEmptyAsString("permission_name");
      String description = node.getValueOrEmptyAsString("permission_description");
      Permission permission = new Permission(id, name, description);
      permissions.add(permission);
    }

    return permissions;
  }
}
