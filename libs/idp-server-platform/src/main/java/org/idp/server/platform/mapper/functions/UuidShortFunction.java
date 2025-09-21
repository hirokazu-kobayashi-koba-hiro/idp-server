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

import java.security.SecureRandom;
import java.util.Map;

/**
 * {@code UuidShortFunction} generates a shortened, URL-safe unique identifier.
 *
 * <p>This function creates a shorter alternative to standard UUIDs, useful for cases where a
 * compact identifier is needed (e.g., session IDs, temporary tokens, short URLs).
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>length</b>: Length of the generated identifier (default: 8, range: 4-32)
 *   <li><b>uppercase</b>: Whether to use uppercase letters (default: false)
 *   <li><b>exclude_ambiguous</b>: Exclude ambiguous characters like 0, O, 1, l (default: true)
 * </ul>
 *
 * <p>Character sets:
 *
 * <ul>
 *   <li>Default: {@code "abcdefghijkmnpqrstuvwxyz23456789"} (excludes ambiguous chars)
 *   <li>With ambiguous: {@code "abcdefghijklmnopqrstuvwxyz0123456789"}
 *   <li>Uppercase: converts to uppercase when enabled
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * {
 *   "name": "uuid_short",
 *   "args": {
 *     "length": 12,
 *     "uppercase": true,
 *     "exclude_ambiguous": false
 *   }
 * }
 * }</pre>
 *
 * <p>Output examples:
 *
 * <ul>
 *   <li>Default (8 chars): {@code "a3k7m9x2"}
 *   <li>Uppercase: {@code "A3K7M9X2"}
 *   <li>With ambiguous: {@code "a3k7o0l1"}
 * </ul>
 */
public class UuidShortFunction implements ValueFunction {

  private static final String SAFE_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
  private static final String FULL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
  private static final int DEFAULT_LENGTH = 8;
  private static final int MIN_LENGTH = 4;
  private static final int MAX_LENGTH = 32;

  private final SecureRandom random = new SecureRandom();

  /**
   * Generates a short UUID-like identifier.
   *
   * @param input the input value (ignored)
   * @param args function arguments for customization
   * @return a short, URL-safe identifier string
   * @throws IllegalArgumentException if length is out of valid range
   */
  @Override
  public Object apply(Object input, Map<String, Object> args) {
    int length = getIntArg(args, "length", DEFAULT_LENGTH);
    boolean uppercase = getBooleanArg(args, "uppercase", false);
    boolean excludeAmbiguous = getBooleanArg(args, "exclude_ambiguous", true);

    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Length must be between " + MIN_LENGTH + " and " + MAX_LENGTH + ", got: " + length);
    }

    String alphabet = excludeAmbiguous ? SAFE_ALPHABET : FULL_ALPHABET;
    StringBuilder result = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      int index = random.nextInt(alphabet.length());
      result.append(alphabet.charAt(index));
    }

    return uppercase ? result.toString().toUpperCase() : result.toString();
  }

  @Override
  public String name() {
    return "uuid_short";
  }

  /** Helper method to extract integer argument with default value. */
  private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Number) return ((Number) value).intValue();
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** Helper method to extract boolean argument with default value. */
  private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
    if (args == null) return defaultValue;
    Object value = args.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Boolean) return (Boolean) value;
    return Boolean.parseBoolean(value.toString());
  }
}
