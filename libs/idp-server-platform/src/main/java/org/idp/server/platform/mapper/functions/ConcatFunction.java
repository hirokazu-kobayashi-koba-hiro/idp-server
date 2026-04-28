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

import java.util.List;
import java.util.Map;

/**
 * {@code ConcatFunction} concatenates multiple values into a single string.
 *
 * <p>The {@code values} argument is a list of elements to concatenate. Each element is converted to
 * a string via {@code toString()}. When used with dynamic args resolution ({@code
 * MappingRuleObjectMapper.resolveArgs} or reshape's per-field resolveArgs), elements starting with
 * "$." are resolved from the context before this function is called.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Basic concatenation
 * {"name": "concat", "args": {"values": ["Hello", " ", "World"]}}
 * // → "Hello World"
 *
 * // With dynamic args (resolved before concat is called)
 * {"name": "concat", "args": {"values": ["$.first_name", " ", "$.last_name"]}}
 * // → "Taro Tanaka"
 * }</pre>
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>values</b> (required): List of values to concatenate
 *   <li><b>separator</b> (optional, default: ""): String to insert between values
 *   <li><b>skipNull</b> (optional, default: false): Skip null values
 *   <li><b>skipEmpty</b> (optional, default: false): Skip empty string values
 * </ul>
 */
public class ConcatFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("concat: 'values' argument is required");
    }

    Object valuesArg = args.get("values");
    if (valuesArg == null || !(valuesArg instanceof List)) {
      throw new IllegalArgumentException("concat: 'values' argument must be a List");
    }

    List<?> values = (List<?>) valuesArg;
    String separator = getStringArg(args, "separator", "");
    boolean skipNull = getBooleanArg(args, "skipNull", false);
    boolean skipEmpty = getBooleanArg(args, "skipEmpty", false);

    StringBuilder sb = new StringBuilder();
    boolean first = true;

    for (Object value : values) {
      if (value == null) {
        if (skipNull) {
          continue;
        }
        value = "";
      }

      String str = value.toString();
      if (skipEmpty && str.isEmpty()) {
        continue;
      }

      if (!first) {
        sb.append(separator);
      }
      sb.append(str);
      first = false;
    }

    return sb.toString();
  }

  @Override
  public String name() {
    return "concat";
  }

  private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    return value != null ? value.toString() : defaultValue;
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
