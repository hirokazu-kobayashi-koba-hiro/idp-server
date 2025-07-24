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

package org.idp.server.core.extension.identity.verification.configuration.process;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationCondition implements JsonReadable {
  String path;
  String type;
  String operation;
  Object value;

  public IdentityVerificationCondition() {}

  public IdentityVerificationCondition(String path, String type, String operation, Object value) {
    this.path = path;
    this.type = type;
    this.operation = operation;
    this.value = value;
  }

  public String path() {
    return path;
  }

  public String type() {
    return type;
  }

  public String operation() {
    return operation;
  }

  public Object value() {
    return value;
  }

  public boolean exists() {
    return path != null && operation != null && value != null;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("path", path);
    map.put("type", type);
    map.put("operation", operation);
    map.put("value", value);
    return map;
  }
}
