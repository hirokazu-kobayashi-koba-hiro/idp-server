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


package org.idp.server.platform.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SecurityEventClient {

  String id;
  String name;

  public SecurityEventClient() {}

  public SecurityEventClient(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    if (id != null) {
      result.put("id", id);
    }
    if (name != null) {
      result.put("name", name);
    }
    return result;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.isEmpty();
  }

  public String clientId() {
    return id;
  }
}
