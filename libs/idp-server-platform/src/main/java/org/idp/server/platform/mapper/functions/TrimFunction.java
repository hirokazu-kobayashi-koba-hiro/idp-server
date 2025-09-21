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
 * {@code TrimFunction} removes whitespace and specified characters from string edges.
 *
 * <p>This function provides flexible trimming operations with support for custom character sets,
 * directional trimming (start/end only), and various whitespace handling modes.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>mode</b>: Trimming mode (default: "both"). Options: "both", "start", "end"
 *   <li><b>chars</b>: Characters to remove (default: whitespace). Can be string of characters
 *   <li><b>whitespace</b>: Whether to include whitespace in trimming (default: true)
 *   <li><b>normalize</b>: Whether to normalize internal whitespace to single spaces (default:
 *       false)
 * </ul>
 *
 * <p>Trimming modes:
 *
 * <ul>
 *   <li><b>both</b>: Remove characters from both start and end (default)
 *   <li><b>start</b>: Remove characters from start only (left trim)
 *   <li><b>end</b>: Remove characters from end only (right trim)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Basic whitespace trimming
 * {"name": "trim"}
 *
 * // Trim specific characters
 * {"name": "trim", "args": {"chars": ".,;:"}}
 *
 * // Trim only from start
 * {"name": "trim", "args": {"mode": "start"}}
 *
 * // Trim punctuation and whitespace
 * {"name": "trim", "args": {"chars": ".,;:", "whitespace": true}}
 *
 * // Normalize internal whitespace while trimming
 * {"name": "trim", "args": {"normalize": true}}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: " hello world " → both: "hello world"
 *   <li>Input: "...hello..." → chars="." → "hello"
 *   <li>Input: " hello world " → start: "hello world "
 *   <li>Input: "hello world" → normalize=true: "hello world"
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>User input sanitization and normalization
 *   <li>CSV/TSV data cleaning (remove quotes, spaces)
 *   <li>URL path cleaning (remove slashes, dots)
 *   <li>Text preprocessing for search and indexing
 * </ul>
 */
public class TrimFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    String str = input.toString();
    if (str.isEmpty()) {
      return str;
    }

    String mode = getStringArg(args, "mode", "both");
    String customChars = getStringArg(args, "chars", null);
    boolean includeWhitespace = getBooleanArg(args, "whitespace", true);
    boolean normalize = getBooleanArg(args, "normalize", false);

    // Build character set to trim
    String charsToTrim = buildCharSetToTrim(customChars, includeWhitespace);

    // Apply trimming based on mode
    String result;
    switch (mode.toLowerCase()) {
      case "both":
        result = trimBoth(str, charsToTrim, includeWhitespace);
        break;
      case "start":
        result = trimStart(str, charsToTrim, includeWhitespace);
        break;
      case "end":
        result = trimEnd(str, charsToTrim, includeWhitespace);
        break;
      default:
        throw new IllegalArgumentException(
            "trim: Invalid mode '" + mode + "'. Supported modes: both, start, end");
    }

    // Apply normalization if requested
    if (normalize) {
      result = normalizeWhitespace(result);
    }

    return result;
  }

  @Override
  public String name() {
    return "trim";
  }

  /**
   * Builds the character set to trim based on custom chars and whitespace settings.
   *
   * @param customChars custom characters to trim (null for none)
   * @param includeWhitespace whether to include whitespace characters
   * @return string containing all characters to trim
   */
  private String buildCharSetToTrim(String customChars, boolean includeWhitespace) {
    StringBuilder chars = new StringBuilder();

    if (includeWhitespace) {
      // Add common whitespace characters
      chars.append(
          " \t\n\r\f\u00A0\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u200B\u2028\u2029");
    }

    if (customChars != null) {
      chars.append(customChars);
    }

    return chars.toString();
  }

  /**
   * Trims characters from both start and end of string.
   *
   * @param str input string
   * @param charsToTrim characters to remove
   * @return trimmed string
   */
  private String trimBoth(String str, String charsToTrim, boolean includeWhitespace) {
    return trimEnd(trimStart(str, charsToTrim, includeWhitespace), charsToTrim, includeWhitespace);
  }

  /**
   * Trims characters from start of string.
   *
   * @param str input string
   * @param charsToTrim characters to remove
   * @return trimmed string
   */
  private String trimStart(String str, String charsToTrim, boolean includeWhitespace) {
    int start = 0;
    while (start < str.length()
        && shouldTrimChar(str.charAt(start), charsToTrim, includeWhitespace)) {
      start++;
    }
    return str.substring(start);
  }

  /**
   * Trims characters from end of string.
   *
   * @param str input string
   * @param charsToTrim characters to remove
   * @return trimmed string
   */
  private String trimEnd(String str, String charsToTrim, boolean includeWhitespace) {
    int end = str.length();
    while (end > 0 && shouldTrimChar(str.charAt(end - 1), charsToTrim, includeWhitespace)) {
      end--;
    }
    return str.substring(0, end);
  }

  /**
   * Checks if character should be trimmed.
   *
   * @param c character to check
   * @param charsToTrim string containing characters to trim
   * @param includeWhitespace whether to include whitespace in trimming
   * @return true if character should be trimmed
   */
  private boolean shouldTrimChar(char c, String charsToTrim, boolean includeWhitespace) {
    if (charsToTrim.isEmpty()) {
      return includeWhitespace && Character.isWhitespace(c);
    }
    return charsToTrim.indexOf(c) >= 0;
  }

  /**
   * Normalizes internal whitespace to single spaces.
   *
   * @param str input string
   * @return string with normalized whitespace
   */
  private String normalizeWhitespace(String str) {
    if (str.isEmpty()) {
      return str;
    }

    StringBuilder result = new StringBuilder();
    boolean inWhitespace = false;

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      if (Character.isWhitespace(c)) {
        if (!inWhitespace) {
          result.append(' ');
          inWhitespace = true;
        }
      } else {
        result.append(c);
        inWhitespace = false;
      }
    }

    return result.toString();
  }

  /**
   * Helper method to extract string argument with default value.
   *
   * @param args argument map
   * @param key argument key
   * @param defaultValue default value if key not found
   * @return string value
   */
  private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    return value != null ? value.toString() : defaultValue;
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
