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

package org.idp.server.platform.mapper.functions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

/**
 * {@code ReshapeFunction} transforms a single object (Map) into a new shape by extracting,
 * renaming, and optionally transforming fields.
 *
 * <p>Each field value in the {@code fields} argument can be:
 *
 * <ul>
 *   <li>A JSONPath string (e.g., {@code "$.entity_id"}) — resolved against the input object
 *   <li>A static value (non-string or string not starting with "$.")
 *   <li>A Map with {@code from}/{@code static_value} and optional {@code functions} — a
 *       mapping_rule subset that enables per-field value transformation
 * </ul>
 *
 * <p>Example with per-field functions:
 *
 * <pre>{@code
 * {
 *   "name": "reshape",
 *   "args": {
 *     "fields": {
 *       "id": "$.entity_id",
 *       "name": "$.entity_name",
 *       "amount": {
 *         "from": "$.amount_str",
 *         "functions": [{"name": "convert_type", "args": {"type": "integer"}}]
 *       },
 *       "status": {
 *         "from": "$.raw_status",
 *         "functions": [{"name": "switch", "args": {"cases": {"A": "active", "I": "inactive"}}}]
 *       },
 *       "tag": {"static_value": "external"}
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>fields</b> (required): A Map defining the output shape. Keys are output field names.
 * </ul>
 */
public class ReshapeFunction implements ValueFunction {

  private FunctionRegistry functionRegistry;

  public void setFunctionRegistry(FunctionRegistry functionRegistry) {
    this.functionRegistry = functionRegistry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("reshape: 'fields' argument is required");
    }

    Object fieldsArg = args.get("fields");
    if (fieldsArg == null || !(fieldsArg instanceof Map)) {
      throw new IllegalArgumentException("reshape: 'fields' argument must be a Map");
    }

    if (input == null) {
      return null;
    }

    if (!(input instanceof Map)) {
      return null;
    }

    Map<String, Object> fields = (Map<String, Object>) fieldsArg;
    Map<String, Object> inputMap = (Map<String, Object>) input;

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(inputMap);
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());

    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Object> field : fields.entrySet()) {
      String outputKey = field.getKey();
      Object fieldSpec = field.getValue();

      Object value = resolveFieldValue(fieldSpec, jsonPath);
      result.put(outputKey, value);
    }

    return result;
  }

  @Override
  public String name() {
    return "reshape";
  }

  /**
   * Resolve a field value from the field specification.
   *
   * <p>Three forms:
   *
   * <ol>
   *   <li>String starting with "$." → JSONPath evaluation
   *   <li>Map with "from"/"static_value" + optional "functions" → mapping_rule subset
   *   <li>Other → static value
   * </ol>
   */
  @SuppressWarnings("unchecked")
  private Object resolveFieldValue(Object fieldSpec, JsonPathWrapper jsonPath) {
    // Form 1: JSONPath string
    if (fieldSpec instanceof String str && str.startsWith("$.")) {
      return jsonPath.readRaw(str);
    }

    // Form 2: mapping_rule subset { from, static_value, functions }
    if (fieldSpec instanceof Map) {
      Map<String, Object> spec = (Map<String, Object>) fieldSpec;
      Object value = resolveBaseValue(spec, jsonPath);
      return applyFunctions(spec, value);
    }

    // Form 3: static value
    return fieldSpec;
  }

  private Object resolveBaseValue(Map<String, Object> spec, JsonPathWrapper jsonPath) {
    if (spec.containsKey("static_value")) {
      return spec.get("static_value");
    }
    Object from = spec.get("from");
    if (from instanceof String str && str.startsWith("$.")) {
      return jsonPath.readRaw(str);
    }
    return from;
  }

  @SuppressWarnings("unchecked")
  private Object applyFunctions(Map<String, Object> spec, Object value) {
    Object functionsObj = spec.get("functions");
    if (functionsObj == null || !(functionsObj instanceof List)) {
      return value;
    }
    if (functionRegistry == null) {
      return value;
    }

    List<Map<String, Object>> functionSpecs = (List<Map<String, Object>>) functionsObj;
    Object v = value;
    for (Map<String, Object> funcSpec : functionSpecs) {
      String funcName = (String) funcSpec.get("name");
      Map<String, Object> funcArgs = (Map<String, Object>) funcSpec.get("args");
      if (funcArgs == null) {
        funcArgs = new HashMap<>();
      }

      ValueFunction fn = functionRegistry.get(funcName);
      if (fn == null) {
        continue;
      }
      v = fn.apply(v, funcArgs);
    }
    return v;
  }
}
