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
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.functions.FunctionRegistry;
import org.idp.server.platform.mapper.functions.ValueFunction;

public class MappingRuleObjectMapper {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(MappingRuleObjectMapper.class);
  private static final FunctionRegistry functionRegistry = new FunctionRegistry();

  public static Map<String, Object> execute(
      List<MappingRule> mappingRules, JsonPathWrapper jsonPath) {

    Map<String, Object> flatMap = new HashMap<>();

    for (MappingRule rule : mappingRules) {
      // Check condition before executing the rule
      if (!rule.shouldExecute(jsonPath)) {
        log.debug("Rule skipped due to condition: to={}", rule.to());
        continue;
      }

      Object baseValue = resolveBaseValue(rule, jsonPath);
      Object finalValue = applyFunctions(rule, baseValue);
      writeResult(rule, finalValue, flatMap);
    }

    return new ObjectCompositor(flatMap).composite();
  }

  /** Resolve base value from staticValue, from(JSONPath) or null. */
  static Object resolveBaseValue(MappingRule rule, JsonPathWrapper jsonPath) {
    if (rule.hasStaticValue()) {
      log.debug("Apply static value: to={}", rule.to());
      return rule.staticValue();
    }
    if (rule.hasFrom()) {
      log.debug("Read from JSONPath: from={}, to={}", rule.from(), rule.to());
      return jsonPath.readRaw(rule.from());
    }
    // no static/from → start from null
    return null;
  }

  /** Apply all configured functions sequentially. */
  static Object applyFunctions(MappingRule rule, Object value) {
    if (!rule.hasFunctions()) {
      return value;
    }
    Object v = value;
    for (FunctionSpec spec : rule.functions()) {
      ValueFunction fn = functionRegistry.get(spec.name());
      if (fn == null) {
        log.warn("Function not found: name={}, to={}", spec.name(), rule.to());
        continue;
      }
      v = fn.apply(v, spec.args());
    }
    return v;
  }

  /** Write the final value to the map, supporting "*" expansion. */
  static void writeResult(MappingRule rule, Object value, Map<String, Object> flatMap) {
    if ("*".equals(rule.to())) {
      if (value instanceof Map<?, ?> mapValue) {
        for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
          if (entry.getKey() instanceof String key) {
            flatMap.put(key, entry.getValue());
          }
        }
      } else if (value == null) {
        log.warn(
            "'*' skipped: value is null (from=" + (rule.hasFrom() ? rule.from() : "n/a") + ")");
      } else {
        log.warn("'*' requires Map but got: type={}", value.getClass().getSimpleName());
      }
    } else {
      flatMap.put(rule.to(), value);
    }
  }
}
