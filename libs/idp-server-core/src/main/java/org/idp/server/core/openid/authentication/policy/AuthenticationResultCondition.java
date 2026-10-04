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

package org.idp.server.core.openid.authentication.policy;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationResultCondition implements JsonReadable {
  String path;
  String type;
  String operation;
  Object value;

  /**
   * Issue #1907: a path to compare {@code path} with, in place of the literal {@code value}. Only
   * {@code eq} and {@code ne} take it.
   */
  String valuePath;

  public AuthenticationResultCondition() {}

  public AuthenticationResultCondition(String path, String type, String operation, Object value) {
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

  public String valuePath() {
    return valuePath;
  }

  public boolean hasValuePath() {
    return valuePath != null && !valuePath.isEmpty();
  }

  /**
   * @return why {@code value_path} is misused here, or null when it is not: it replaces {@code
   *     value}, so the two cannot both be set, and only {@code eq} / {@code ne} compare two paths
   */
  public String valuePathViolation() {
    if (!hasValuePath()) {
      return null;
    }
    if (value != null) {
      return "value and value_path cannot both be set: " + path;
    }
    ConditionOperation conditionOperation = ConditionOperation.from(operation);
    if (conditionOperation != ConditionOperation.EQ
        && conditionOperation != ConditionOperation.NE) {
      return "value_path supports only eq and ne: " + path + " " + operation;
    }
    return null;
  }

  public boolean exists() {
    return path != null && operation != null && (value != null || hasValuePath());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("path", path);
    map.put("type", type);
    map.put("operation", operation);
    map.put("value", value);
    if (hasValuePath()) map.put("value_path", valuePath);
    return map;
  }
}
