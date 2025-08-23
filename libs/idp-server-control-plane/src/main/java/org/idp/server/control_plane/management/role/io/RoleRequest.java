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

package org.idp.server.control_plane.management.role.io;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;

public class RoleRequest {

  JsonNodeWrapper json;

  public RoleRequest(Map<String, Object> values) {
    this.json = JsonNodeWrapper.fromMap(values);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public String id() {
    return json.getValueOrEmptyAsString("id");
  }

  public boolean hasId() {
    return json.contains("id");
  }

  public String name() {
    return json.getValueOrEmptyAsString("name");
  }

  public String description() {
    return json.getValueOrEmptyAsString("description");
  }

  public List<String> permissions() {
    JsonNodeWrapper permissions = json.getNode("permissions");
    return permissions.toList();
  }
}
