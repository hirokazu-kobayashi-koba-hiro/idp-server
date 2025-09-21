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

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code MapFunction} transforms each element of a collection by applying a specified function.
 *
 * <p>This function takes a collection input and applies a transformation function to each element,
 * returning a new collection with the transformed elements. It supports various input types
 * including arrays, lists, sets, and can handle nested function calls.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>function</b>: Name of the function to apply to each element (required)
 *   <li><b>function_args</b>: Arguments to pass to the transformation function (optional)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Transform array elements with format function
 * {"name": "map", "args": {
 *   "function": "format",
 *   "function_args": {
 *     "template": "role:{{value}}"
 *   }
 * }}
 *
 * // Apply case conversion to each element
 * {"name": "map", "args": {
 *   "function": "case",
 *   "function_args": {
 *     "mode": "upper"
 *   }
 * }}
 *
 * // Apply trim function to each string element
 * {"name": "map", "args": {
 *   "function": "trim"
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: ["admin", "user"], function: "format", template: "role:{{value}}" → ["role:admin",
 *       "role:user"]
 *   <li>Input: ["hello", "world"], function: "case", mode: "upper" → ["HELLO", "WORLD"]
 *   <li>Input: [" a ", " b "], function: "trim" → ["a", "b"]
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>User permission list transformation
 *   <li>Role prefix/suffix addition
 *   <li>Data normalization across collections
 *   <li>Batch string processing operations
 * </ul>
 */
public class MapFunction implements ValueFunction {

  private FunctionRegistry functionRegistry;

  public void setFunctionRegistry(FunctionRegistry functionRegistry) {
    this.functionRegistry = functionRegistry;
  }

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("map: 'function' argument is required");
    }

    String functionName = getStringArg(args, "function");
    if (functionName == null || functionName.isEmpty()) {
      throw new IllegalArgumentException("map: 'function' argument is required");
    }

    ValueFunction targetFunction = functionRegistry.get(functionName);
    if (targetFunction == null) {
      throw new IllegalArgumentException("map: Unknown function '" + functionName + "'");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> functionArgs = (Map<String, Object>) args.get("function_args");
    if (functionArgs == null) {
      functionArgs = new HashMap<>();
    }

    Collection<?> collection = toCollection(input);
    if (collection == null) {
      return null;
    }

    List<Object> result = new ArrayList<>();
    for (Object element : collection) {
      try {
        Object transformedElement = targetFunction.apply(element, functionArgs);
        result.add(transformedElement);
      } catch (Exception e) {
        throw new RuntimeException(
            "map: Error applying function '" + functionName + "' to element: " + element, e);
      }
    }

    return result;
  }

  @Override
  public String name() {
    return "map";
  }

  /**
   * Converts input to a Collection.
   *
   * @param input the input value
   * @return Collection representation of input, or null if input is null
   */
  private Collection<?> toCollection(Object input) {
    if (input == null) {
      return null;
    }

    if (input instanceof Collection<?>) {
      return (Collection<?>) input;
    }

    if (input instanceof Object[]) {
      return Arrays.asList((Object[]) input);
    }

    if (input instanceof int[]) {
      return Arrays.stream((int[]) input).boxed().collect(Collectors.toList());
    }

    if (input instanceof long[]) {
      return Arrays.stream((long[]) input).boxed().collect(Collectors.toList());
    }

    if (input instanceof double[]) {
      return Arrays.stream((double[]) input).boxed().collect(Collectors.toList());
    }

    if (input instanceof boolean[]) {
      boolean[] arr = (boolean[]) input;
      List<Boolean> list = new ArrayList<>();
      for (boolean b : arr) {
        list.add(b);
      }
      return list;
    }

    // Single element - treat as single-element collection
    return Collections.singletonList(input);
  }

  /**
   * Helper method to extract string argument.
   *
   * @param args argument map
   * @param key argument key
   * @return string value or null if not found
   */
  private static String getStringArg(Map<String, Object> args, String key) {
    if (args == null) return null;
    Object value = args.get(key);
    return value != null ? value.toString() : null;
  }
}
