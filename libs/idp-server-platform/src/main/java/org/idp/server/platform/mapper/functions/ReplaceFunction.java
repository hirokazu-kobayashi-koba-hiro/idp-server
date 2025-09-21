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

import java.util.Map;

/**
 * {@code ReplaceFunction} performs literal string replacement operations.
 *
 * <p>This function replaces all occurrences of a target string with a replacement string. It
 * performs literal (non-regex) replacement for safety and predictability.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>target</b>: String to search for (required)
 *   <li><b>replacement</b>: String to replace with (required)
 *   <li><b>ignoreCase</b>: Whether to perform case-insensitive search (default: false)
 *   <li><b>replaceFirst</b>: Whether to replace only the first occurrence (default: false)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Replace all occurrences
 * {"name": "replace", "args": {"target": "old", "replacement": "new"}}
 *
 * // Case-insensitive replacement
 * {"name": "replace", "args": {"target": "OLD", "replacement": "new", "ignoreCase": true}}
 *
 * // Replace only first occurrence
 * {"name": "replace", "args": {"target": "test", "replacement": "demo", "replaceFirst": true}}
 *
 * // Remove text (replace with empty string)
 * {"name": "replace", "args": {"target": "unwanted", "replacement": ""}}
 * }</pre>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Email domain migration: Replace old domain with new domain
 *   <li>Phone number formatting: Replace separators or add prefixes
 *   <li>Username normalization: Remove or replace special characters
 *   <li>Text sanitization: Remove unwanted strings
 * </ul>
 */
public class ReplaceFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    if (args == null) {
      throw new IllegalArgumentException(
          "replace: 'target' and 'replacement' arguments are required");
    }

    String str = input.toString();
    String target = getStringArg(args, "target");
    String replacement = getStringArg(args, "replacement");

    if (target == null) {
      throw new IllegalArgumentException("replace: 'target' argument is required");
    }
    if (replacement == null) {
      throw new IllegalArgumentException("replace: 'replacement' argument is required");
    }

    if (target.isEmpty() || str.isEmpty()) {
      return str;
    }

    boolean ignoreCase = getBooleanArg(args, "ignoreCase", false);
    boolean replaceFirst = getBooleanArg(args, "replaceFirst", false);

    if (ignoreCase) {
      // Case-insensitive replacement
      if (replaceFirst) {
        return replaceFirstIgnoreCase(str, target, replacement);
      } else {
        return replaceAllIgnoreCase(str, target, replacement);
      }
    } else {
      // Case-sensitive replacement
      if (replaceFirst) {
        int index = str.indexOf(target);
        if (index == -1) {
          return str;
        }
        return str.substring(0, index) + replacement + str.substring(index + target.length());
      } else {
        return str.replace(target, replacement);
      }
    }
  }

  @Override
  public String name() {
    return "replace";
  }

  /**
   * Performs case-insensitive replacement of all occurrences.
   *
   * @param str input string
   * @param target string to search for
   * @param replacement replacement string
   * @return string with all occurrences replaced
   */
  private String replaceAllIgnoreCase(String str, String target, String replacement) {
    StringBuilder result = new StringBuilder();
    String lowerStr = str.toLowerCase();
    String lowerTarget = target.toLowerCase();
    int targetLen = target.length();
    int start = 0;

    while (start < str.length()) {
      int index = lowerStr.indexOf(lowerTarget, start);
      if (index == -1) {
        result.append(str.substring(start));
        break;
      }

      result.append(str.substring(start, index));
      result.append(replacement);
      start = index + targetLen;
    }

    return result.toString();
  }

  /**
   * Performs case-insensitive replacement of first occurrence only.
   *
   * @param str input string
   * @param target string to search for
   * @param replacement replacement string
   * @return string with first occurrence replaced
   */
  private String replaceFirstIgnoreCase(String str, String target, String replacement) {
    String lowerStr = str.toLowerCase();
    String lowerTarget = target.toLowerCase();
    int index = lowerStr.indexOf(lowerTarget);

    if (index == -1) {
      return str;
    }

    return str.substring(0, index) + replacement + str.substring(index + target.length());
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
   * @param defaultValue default value if key not found
   * @return boolean value
   */
  private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Boolean) return (Boolean) value;
    return Boolean.parseBoolean(value.toString());
  }
}
