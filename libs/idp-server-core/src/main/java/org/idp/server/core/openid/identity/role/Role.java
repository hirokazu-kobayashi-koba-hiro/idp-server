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

package org.idp.server.core.openid.identity.role;

import java.io.Serializable;
import java.util.*;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.platform.uuid.UuidConvertable;

public class Role implements Serializable, UuidConvertable {
  String id;
  String name;
  String description;
  List<Permission> permissions;

  public Role() {}

  public Role(String id, String name, String description, List<Permission> permissions) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.permissions = permissions;
  }

  public String id() {
    return id;
  }

  public UUID idAsUuid() {
    return convertUuid(id);
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public List<Permission> permissions() {
    return permissions;
  }

  public List<Map<String, Object>> permissionsAsMap() {
    return permissions.stream().map(Permission::toMap).toList();
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }

  public boolean match(Role role) {
    if (!exists()) return false;

    return this.name.equals(role.name());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("description", description);
    map.put("permissions", permissionsAsMap());
    return map;
  }
}
