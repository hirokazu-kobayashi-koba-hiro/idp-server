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

package org.idp.server.platform.http;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.json.JsonReadable;

/**
 * Represents a single condition to evaluate against response body.
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * {
 *   "path": "$.status",
 *   "operation": "eq",
 *   "value": "success"
 * }
 * }</pre>
 */
public class ResponseCondition implements JsonReadable {
  public String path;
  public String operation;
  public Object value;

  public ResponseCondition() {}

  public ResponseCondition(String path, ConditionOperation operation, Object value) {
    this.path = path;
    this.operation = operation.name().toLowerCase();
    this.value = value;
  }

  public String path() {
    return path;
  }

  public ConditionOperation operation() {
    return ConditionOperation.from(operation);
  }

  public Object value() {
    return value;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("path", path);
    map.put("operation", operation);
    map.put("value", value);
    return map;
  }
}
