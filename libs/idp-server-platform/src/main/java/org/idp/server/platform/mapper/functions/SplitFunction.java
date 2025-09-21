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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * {@code SplitFunction} divides a string into an array of substrings using a specified separator.
 *
 * <p>This function takes a string input and splits it into multiple elements based on the provided
 * separator pattern. It supports various splitting options including regex patterns, trimming
 * whitespace, and filtering empty elements.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>separator</b>: String or regex pattern used to split the input (optional, defaults to
 *       ",")
 *   <li><b>trim</b>: Whether to trim whitespace from each element (optional, defaults to false)
 *   <li><b>removeEmpty</b>: Whether to remove empty elements from result (optional, defaults to
 *       false)
 *   <li><b>limit</b>: Maximum number of elements to return (optional, defaults to no limit)
 *   <li><b>regex</b>: Whether separator should be treated as regex pattern (optional, defaults to
 *       false)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Basic splitting with comma
 * {"name": "split", "args": {
 *   "separator": ","
 * }}
 *
 * // Split with trimming
 * {"name": "split", "args": {
 *   "separator": ",",
 *   "trim": true
 * }}
 *
 * // Split with custom separator and remove empty
 * {"name": "split", "args": {
 *   "separator": " | ",
 *   "removeEmpty": true
 * }}
 *
 * // Split with limit
 * {"name": "split", "args": {
 *   "separator": ":",
 *   "limit": 2
 * }}
 *
 * // Split with regex pattern
 * {"name": "split", "args": {
 *   "separator": "\\s+",
 *   "regex": true,
 *   "trim": true
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: "admin,user,guest", separator: "," → ["admin", "user", "guest"]
 *   <li>Input: " a , b , c ", separator: ",", trim: true → ["a", "b", "c"]
 *   <li>Input: "role1||role2||", separator: "||", removeEmpty: true → ["role1", "role2"]
 *   <li>Input: "key:value:extra", separator: ":", limit: 2 → ["key", "value:extra"]
 *   <li>Input: "a b c", separator: "\\s+", regex: true → ["a", "b", "c"]
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>CSV data parsing
 *   <li>Permission string decomposition
 *   <li>Multi-value attribute parsing
 *   <li>Tag list extraction
 * </ul>
 */
public class SplitFunction implements ValueFunction {

  // Cache for compiled regex patterns to improve performance
  private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

  // Maximum regex length to prevent ReDoS attacks
  private static final int MAX_REGEX_LENGTH = 100;

  // Maximum cache size to prevent memory issues
  private static final int MAX_CACHE_SIZE = 1000;

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    String inputStr = input.toString();
    if (inputStr.isEmpty()) {
      return Collections.emptyList();
    }

    String separator = getStringArg(args, "separator");
    if (separator == null) {
      separator = ",";
    }

    boolean trim = getBooleanArg(args, "trim", false);
    boolean removeEmpty = getBooleanArg(args, "removeEmpty", false);
    int limit = getIntArg(args, "limit", -1);
    boolean regex = getBooleanArg(args, "regex", false);

    String[] parts;
    if (regex) {
      Pattern pattern = getCachedPattern(separator);
      if (limit > 0) {
        parts = pattern.split(inputStr, limit);
      } else {
        parts = pattern.split(inputStr);
      }
    } else {
      if (limit > 0) {
        parts = inputStr.split(Pattern.quote(separator), limit);
      } else {
        parts = inputStr.split(Pattern.quote(separator));
      }
    }

    List<String> result = new ArrayList<>();
    for (String part : parts) {
      if (trim) {
        part = part.trim();
      }

      if (removeEmpty && part.isEmpty()) {
        continue;
      }

      result.add(part);
    }

    return result;
  }

  @Override
  public String name() {
    return "split";
  }

  /**
   * Gets a cached compiled regex pattern, with ReDoS protection.
   *
   * @param regex the regex pattern string
   * @return compiled Pattern object
   * @throws IllegalArgumentException if regex is too long or invalid
   */
  private static Pattern getCachedPattern(String regex) {
    // Prevent ReDoS attacks by limiting regex length
    if (regex.length() > MAX_REGEX_LENGTH) {
      throw new IllegalArgumentException(
          "split: Regex pattern too long (max " + MAX_REGEX_LENGTH + " characters)");
    }

    return PATTERN_CACHE.computeIfAbsent(
        regex,
        regexKey -> {
          // Prevent memory exhaustion by limiting cache size
          if (PATTERN_CACHE.size() >= MAX_CACHE_SIZE) {
            // Clear some entries (simple strategy - clear all when full)
            PATTERN_CACHE.clear();
          }
          try {
            return Pattern.compile(regexKey);
          } catch (Exception e) {
            throw new IllegalArgumentException("split: Invalid regex pattern: " + regexKey, e);
          }
        });
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

  /**
   * Helper method to extract integer argument with default value.
   *
   * @param args argument map
   * @param key argument key
   * @param defaultValue default value if not found or not integer
   * @return integer value or default
   */
  private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }
}
