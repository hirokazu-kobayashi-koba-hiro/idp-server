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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@code RegexReplaceFunction} performs pattern-based string replacement using regular expressions.
 *
 * <p>This function provides powerful pattern matching and replacement capabilities with support for
 * capture groups, flags, and advanced regex features.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>pattern</b>: Regular expression pattern (required)
 *   <li><b>replacement</b>: Replacement string with optional group references (required)
 *   <li><b>flags</b>: Regex flags like "i" (ignore case), "m" (multiline), "s" (dotall)
 *   <li><b>replaceFirst</b>: Whether to replace only the first match (default: false)
 * </ul>
 *
 * <p>Replacement string supports:
 *
 * <ul>
 *   <li><b>$0</b>: Entire match
 *   <li><b>$1, $2, ...</b>: Capture groups
 *   <li><b>$$</b>: Literal dollar sign
 * </ul>
 *
 * <p>Supported flags:
 *
 * <ul>
 *   <li><b>i</b>: Case-insensitive matching
 *   <li><b>m</b>: Multiline mode (^ and $ match line boundaries)
 *   <li><b>s</b>: Dotall mode (. matches line terminators)
 *   <li><b>x</b>: Comments mode (whitespace and comments ignored)
 *   <li><b>d</b>: Unix lines mode
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Extract and reformat phone numbers
 * {"name": "regex_replace", "args": {
 *   "pattern": "(\\d{3})-(\\d{3})-(\\d{4})",
 *   "replacement": "($1) $2-$3"
 * }}
 *
 * // Remove all digits
 * {"name": "regex_replace", "args": {
 *   "pattern": "\\d+",
 *   "replacement": ""
 * }}
 *
 * // Case-insensitive domain replacement
 * {"name": "regex_replace", "args": {
 *   "pattern": "@old\\.domain\\.com",
 *   "replacement": "@new.domain.com",
 *   "flags": "i"
 * }}
 *
 * // Replace first occurrence only
 * {"name": "regex_replace", "args": {
 *   "pattern": "test",
 *   "replacement": "demo",
 *   "replaceFirst": true
 * }}
 * }</pre>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Phone number formatting and normalization
 *   <li>Email address validation and transformation
 *   <li>Data masking and redaction
 *   <li>Complex text pattern extraction and replacement
 * </ul>
 */
public class RegexReplaceFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    if (args == null) {
      throw new IllegalArgumentException(
          "regex_replace: 'pattern' and 'replacement' arguments are required");
    }

    String str = input.toString();
    String patternStr = getStringArg(args, "pattern");
    String replacement = getStringArg(args, "replacement");

    if (patternStr == null) {
      throw new IllegalArgumentException("regex_replace: 'pattern' argument is required");
    }
    if (replacement == null) {
      throw new IllegalArgumentException("regex_replace: 'replacement' argument is required");
    }

    if (str.isEmpty()) {
      return str;
    }

    try {
      String flagsStr = getStringArg(args, "flags");
      int flags = parseFlags(flagsStr);
      boolean replaceFirst = getBooleanArg(args, "replaceFirst", false);

      Pattern pattern = Pattern.compile(patternStr, flags);
      Matcher matcher = pattern.matcher(str);

      if (replaceFirst) {
        return matcher.replaceFirst(replacement);
      } else {
        return matcher.replaceAll(replacement);
      }

    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException(
          "regex_replace: Invalid regex pattern '" + patternStr + "': " + e.getMessage(), e);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "regex_replace: Error during replacement: " + e.getMessage(), e);
    }
  }

  @Override
  public String name() {
    return "regex_replace";
  }

  /**
   * Parses regex flags from string representation.
   *
   * @param flagsStr flags string (e.g., "im" for ignore case + multiline)
   * @return combined flags as integer
   */
  private int parseFlags(String flagsStr) {
    if (flagsStr == null || flagsStr.isEmpty()) {
      return 0;
    }

    int flags = 0;
    for (char c : flagsStr.toLowerCase().toCharArray()) {
      switch (c) {
        case 'i':
          flags |= Pattern.CASE_INSENSITIVE;
          break;
        case 'm':
          flags |= Pattern.MULTILINE;
          break;
        case 's':
          flags |= Pattern.DOTALL;
          break;
        case 'x':
          flags |= Pattern.COMMENTS;
          break;
        case 'd':
          flags |= Pattern.UNIX_LINES;
          break;
        default:
          // Ignore unknown flags for forward compatibility
          break;
      }
    }
    return flags;
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
