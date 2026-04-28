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
 * {@code PluckFunction} extracts a specific field from each element of an object array.
 *
 * <p>This function takes a collection of objects (Maps) and extracts the value of a specified field
 * from each object, returning a flat list of those values.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>field</b>: The field name to extract from each object (required)
 *   <li><b>skipNull</b>: Whether to skip null values in the result (optional, defaults to false)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Extract account numbers
 * {"name": "pluck", "args": {
 *   "field": "account_no"
 * }}
 *
 * // Extract names, skipping nulls
 * {"name": "pluck", "args": {
 *   "field": "name",
 *   "skipNull": true
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: [{"account_no": "123", "type": "savings"}, {"account_no": "456", "type":
 *       "checking"}], field: "account_no" -> ["123", "456"]
 *   <li>Input: [{"name": "Alice"}, {"name": null}, {"name": "Bob"}], field: "name", skipNull: true
 *       -> ["Alice", "Bob"]
 * </ul>
 */
public class PluckFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("pluck: 'field' argument is required");
    }

    String field = getStringArg(args, "field");
    if (field == null || field.isEmpty()) {
      throw new IllegalArgumentException("pluck: 'field' argument is required");
    }

    boolean skipNull = getBooleanArg(args, "skipNull", false);

    Collection<?> collection = toCollection(input);
    if (collection == null) {
      return null;
    }

    List<Object> result = new ArrayList<>();
    for (Object element : collection) {
      Object value = extractField(element, field);
      if (value == null && skipNull) {
        continue;
      }
      result.add(value);
    }

    return result;
  }

  @Override
  public String name() {
    return "pluck";
  }

  @SuppressWarnings("unchecked")
  private Object extractField(Object element, String field) {
    if (element instanceof Map) {
      return ((Map<String, Object>) element).get(field);
    }
    return null;
  }

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

    return Collections.singletonList(input);
  }

  private static String getStringArg(Map<String, Object> args, String key) {
    if (args == null) return null;
    Object value = args.get(key);
    return value != null ? value.toString() : null;
  }

  private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof String) {
      return Boolean.parseBoolean((String) value);
    }
    return defaultValue;
  }
}
