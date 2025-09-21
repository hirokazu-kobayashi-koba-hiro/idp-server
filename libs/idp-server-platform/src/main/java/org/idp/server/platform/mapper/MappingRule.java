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

package org.idp.server.platform.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.path.JsonPathWrapper;

public class MappingRule implements JsonReadable {
  String from;
  Object staticValue;
  String to;
  List<FunctionSpec> functions;
  ConditionSpec condition;

  public MappingRule() {}

  public MappingRule(String from, String to) {
    this.from = from;
    this.to = to;
  }

  public MappingRule(Object staticValue, String to) {
    this.staticValue = staticValue;
    this.to = to;
  }

  public MappingRule(String from, String to, List<FunctionSpec> functions) {
    this.from = from;
    this.to = to;
    this.functions = functions;
  }

  public MappingRule(Object staticValue, String to, List<FunctionSpec> functions) {
    this.staticValue = staticValue;
    this.to = to;
    this.functions = functions;
  }

  public MappingRule(String from, String to, ConditionSpec condition) {
    this.from = from;
    this.to = to;
    this.condition = condition;
  }

  public MappingRule(Object staticValue, String to, ConditionSpec condition) {
    this.staticValue = staticValue;
    this.to = to;
    this.condition = condition;
  }

  public MappingRule(
      String from, String to, List<FunctionSpec> functions, ConditionSpec condition) {
    this.from = from;
    this.to = to;
    this.functions = functions;
    this.condition = condition;
  }

  public MappingRule(
      Object staticValue, String to, List<FunctionSpec> functions, ConditionSpec condition) {
    this.staticValue = staticValue;
    this.to = to;
    this.functions = functions;
    this.condition = condition;
  }

  public String from() {
    return from;
  }

  public Object staticValue() {
    return staticValue;
  }

  public boolean hasStaticValue() {
    return staticValue != null;
  }

  public boolean hasFrom() {
    return from != null && !from.isEmpty();
  }

  public String to() {
    return to;
  }

  public boolean hasFunctions() {
    return functions != null && !functions.isEmpty();
  }

  public List<FunctionSpec> functions() {
    return functions;
  }

  public ConditionSpec condition() {
    return condition;
  }

  public boolean hasCondition() {
    return condition != null;
  }

  /**
   * Determines whether this mapping rule should be executed based on its condition.
   *
   * @param jsonPath the JSONPath wrapper containing the source data
   * @return true if the rule should be executed, false otherwise
   */
  public boolean shouldExecute(JsonPathWrapper jsonPath) {
    if (!hasCondition()) {
      return true; // No condition means always execute
    }

    try {
      return condition.evaluate(jsonPath);
    } catch (Exception e) {
      // Log the error and default to not executing the rule for safety
      return false;
    }
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("from", from);
    map.put("static_value", staticValue);
    map.put("to", to);
    map.put("functions", functions());
    map.put("condition", condition != null ? condition.toMap() : null);
    return map;
  }
}
