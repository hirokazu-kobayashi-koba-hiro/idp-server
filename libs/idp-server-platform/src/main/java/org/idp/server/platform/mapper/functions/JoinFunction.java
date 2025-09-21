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
 * {@code JoinFunction} combines elements of a collection into a single string using a specified
 * separator.
 *
 * <p>This function takes a collection input and joins all elements into a single string, separated
 * by the specified delimiter. It supports various collection types and provides options for
 * handling null values and empty elements.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>separator</b>: String used to separate elements (optional, defaults to ",")
 *   <li><b>skipNull</b>: Whether to skip null elements (optional, defaults to false)
 *   <li><b>skipEmpty</b>: Whether to skip empty string elements (optional, defaults to false)
 *   <li><b>prefix</b>: String to prepend to the result (optional)
 *   <li><b>suffix</b>: String to append to the result (optional)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Basic joining with comma
 * {"name": "join", "args": {
 *   "separator": ", "
 * }}
 *
 * // Join with custom separator
 * {"name": "join", "args": {
 *   "separator": " | "
 * }}
 *
 * // Join with prefix and suffix
 * {"name": "join", "args": {
 *   "separator": ", ",
 *   "prefix": "[",
 *   "suffix": "]"
 * }}
 *
 * // Skip null and empty values
 * {"name": "join", "args": {
 *   "separator": ",",
 *   "skipNull": true,
 *   "skipEmpty": true
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: ["admin", "user", "guest"], separator: ", " → "admin, user, guest"
 *   <li>Input: ["role1", "role2"], separator: " | ", prefix: "[", suffix: "]" → "[role1 | role2]"
 *   <li>Input: ["a", null, "", "b"], separator: ",", skipNull: true, skipEmpty: true → "a,b"
 *   <li>Input: [1, 2, 3], separator: "-" → "1-2-3"
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>CSV data generation
 *   <li>Permission list formatting
 *   <li>Tag concatenation
 *   <li>Multi-value attribute serialization
 * </ul>
 */
public class JoinFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    Collection<?> collection = toCollection(input);
    if (collection == null) {
      return null;
    }

    String separator = getStringArg(args, "separator");
    if (separator == null) {
      separator = ",";
    }

    boolean skipNull = getBooleanArg(args, "skipNull", false);
    boolean skipEmpty = getBooleanArg(args, "skipEmpty", false);
    String prefix = getStringArg(args, "prefix");
    String suffix = getStringArg(args, "suffix");

    List<String> stringElements = new ArrayList<>();
    for (Object element : collection) {
      if (element == null) {
        if (skipNull) {
          continue;
        }
        stringElements.add("null");
      } else {
        String elementStr = element.toString();
        if (skipEmpty && elementStr.isEmpty()) {
          continue;
        }
        stringElements.add(elementStr);
      }
    }

    String result = String.join(separator, stringElements);

    if (prefix != null) {
      result = prefix + result;
    }
    if (suffix != null) {
      result = result + suffix;
    }

    return result;
  }

  @Override
  public String name() {
    return "join";
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

  /**
   * Helper method to extract boolean argument with default value.
   *
   * @param args argument map
   * @param key argument key
   * @param defaultValue default value if not found or not boolean
   * @return boolean value or default
   */
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
