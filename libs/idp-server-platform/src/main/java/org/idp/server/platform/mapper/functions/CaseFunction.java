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

import java.util.Locale;
import java.util.Map;

/**
 * {@code CaseFunction} performs case conversion operations on strings.
 *
 * <p>This function provides various case conversion modes including uppercase, lowercase, title
 * case, and camel case transformations with locale support.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>mode</b>: Case conversion mode (required). Options: "upper", "lower", "title", "camel",
 *       "pascal"
 *   <li><b>locale</b>: Locale for case conversion (optional, default: "en"). Examples: "en", "tr",
 *       "de"
 *   <li><b>delimiter</b>: Word delimiter for title/camel/pascal case (default: space, underscore,
 *       hyphen)
 * </ul>
 *
 * <p>Supported modes:
 *
 * <ul>
 *   <li><b>upper</b>: Convert to uppercase
 *   <li><b>lower</b>: Convert to lowercase
 *   <li><b>title</b>: Capitalize first letter of each word
 *   <li><b>camel</b>: camelCase (first word lowercase, subsequent words capitalized)
 *   <li><b>pascal</b>: PascalCase (all words capitalized, no spaces)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Convert to uppercase
 * {"name": "case", "args": {"mode": "upper"}}
 *
 * // Convert to lowercase with Turkish locale
 * {"name": "case", "args": {"mode": "lower", "locale": "tr"}}
 *
 * // Convert to title case
 * {"name": "case", "args": {"mode": "title"}}
 *
 * // Convert to camelCase
 * {"name": "case", "args": {"mode": "camel"}}
 *
 * // Convert to PascalCase with custom delimiter
 * {"name": "case", "args": {"mode": "pascal", "delimiter": "-"}}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: "hello world" → upper: "HELLO WORLD"
 *   <li>Input: "HELLO WORLD" → lower: "hello world"
 *   <li>Input: "hello world" → title: "Hello World"
 *   <li>Input: "hello world" → camel: "helloWorld"
 *   <li>Input: "hello-world" → pascal: "HelloWorld"
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Username normalization (lowercase)
 *   <li>Display name formatting (title case)
 *   <li>API property naming (camelCase, PascalCase)
 *   <li>Locale-specific case conversion
 * </ul>
 */
public class CaseFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (input == null) {
      return null;
    }

    if (args == null) {
      throw new IllegalArgumentException("case: 'mode' argument is required");
    }

    String str = input.toString();
    if (str.isEmpty()) {
      return str;
    }

    String mode = getStringArg(args, "mode");
    if (mode == null || mode.isEmpty()) {
      throw new IllegalArgumentException("case: 'mode' argument is required");
    }

    String localeStr = getStringArg(args, "locale");
    Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.ENGLISH;

    String delimiter = getStringArg(args, "delimiter");

    switch (mode.toLowerCase()) {
      case "upper":
        return str.toUpperCase(locale);
      case "lower":
        return str.toLowerCase(locale);
      case "title":
        return toTitleCase(str, delimiter);
      case "camel":
        return toCamelCase(str, delimiter);
      case "pascal":
        return toPascalCase(str, delimiter);
      default:
        throw new IllegalArgumentException(
            "case: Invalid mode '"
                + mode
                + "'. Supported modes: upper, lower, title, camel, pascal");
    }
  }

  @Override
  public String name() {
    return "case";
  }

  /**
   * Converts string to Title Case.
   *
   * @param str input string
   * @param customDelimiter custom word delimiter (null for default)
   * @return title case string
   */
  private String toTitleCase(String str, String customDelimiter) {
    if (str.isEmpty()) return str;

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      if (isWordDelimiter(c, customDelimiter)) {
        result.append(c);
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(Character.toLowerCase(c));
      }
    }

    return result.toString();
  }

  /**
   * Converts string to camelCase.
   *
   * @param str input string
   * @param customDelimiter custom word delimiter (null for default)
   * @return camelCase string
   */
  private String toCamelCase(String str, String customDelimiter) {
    if (str.isEmpty()) return str;

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    boolean firstWord = true;

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      if (isWordDelimiter(c, customDelimiter)) {
        if (!firstWord) {
          capitalizeNext = true;
        }
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else if (firstWord) {
        result.append(Character.toLowerCase(c));
        firstWord = false;
      } else {
        result.append(Character.toLowerCase(c));
      }
    }

    return result.toString();
  }

  /**
   * Converts string to PascalCase.
   *
   * @param str input string
   * @param customDelimiter custom word delimiter (null for default)
   * @return PascalCase string
   */
  private String toPascalCase(String str, String customDelimiter) {
    if (str.isEmpty()) return str;

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;

    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);

      if (isWordDelimiter(c, customDelimiter)) {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(Character.toLowerCase(c));
      }
    }

    return result.toString();
  }

  /**
   * Checks if character is a word delimiter.
   *
   * @param c character to check
   * @param customDelimiter custom delimiter (null for default delimiters)
   * @return true if character is a delimiter
   */
  private boolean isWordDelimiter(char c, String customDelimiter) {
    if (customDelimiter != null) {
      return customDelimiter.indexOf(c) >= 0;
    }

    // Default delimiters: space, underscore, hyphen, dot
    return c == ' ' || c == '_' || c == '-' || c == '.' || Character.isWhitespace(c);
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
