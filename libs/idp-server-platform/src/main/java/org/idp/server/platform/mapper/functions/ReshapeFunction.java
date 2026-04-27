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
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;

/**
 * {@code ReshapeFunction} transforms a single object (Map) into a new shape by extracting and
 * renaming fields.
 *
 * <p>This function takes a Map input and produces a new Map with field names and values defined by
 * the {@code fields} argument. Each field value can be:
 *
 * <ul>
 *   <li>A JSONPath string (e.g., {@code "$.entity_id"}) — resolved against the input object
 *   <li>A static value (non-string or string not starting with "$.")
 * </ul>
 *
 * <p>Combined with {@code map}, this enables per-element object transformation of arrays:
 *
 * <pre>{@code
 * // Rename fields in each element of an array
 * "functions": [
 *   {
 *     "name": "map",
 *     "args": {
 *       "function": "reshape",
 *       "function_args": {
 *         "fields": {
 *           "id": "$.entity_id",
 *           "name": "$.entity_name",
 *           "type": "$.kind"
 *         }
 *       }
 *     }
 *   }
 * ]
 *
 * // Input:  [{"entity_id": "1", "entity_name": "Foo", "kind": "bar"}]
 * // Output: [{"id": "1", "name": "Foo", "type": "bar"}]
 * }</pre>
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>fields</b> (required): A Map defining the output shape. Keys are output field names,
 *       values are JSONPath expressions or static values.
 * </ul>
 */
public class ReshapeFunction implements ValueFunction {

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

    // Create a JsonPathWrapper from the input object for JSONPath evaluation
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
   * <p>If the spec is a String starting with "$.", it's evaluated as JSONPath against the input
   * object. Otherwise, it's treated as a static value.
   */
  private Object resolveFieldValue(Object fieldSpec, JsonPathWrapper jsonPath) {
    if (fieldSpec instanceof String str && str.startsWith("$.")) {
      return jsonPath.readRaw(str);
    }
    return fieldSpec;
  }
}
