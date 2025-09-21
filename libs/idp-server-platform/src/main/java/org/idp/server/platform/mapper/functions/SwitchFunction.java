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
 * {@code SwitchFunction} provides multi-case conditional value mapping based on input matching.
 *
 * <p>This function evaluates the input value against multiple case mappings and returns the
 * corresponding mapped value. If no case matches, it returns the default value or the original
 * input if no default is specified.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>cases</b>: Map of case mappings (key → value) where keys are matched against input
 *       (required)
 *   <li><b>default</b>: Default value to return when no case matches (optional, defaults to input)
 *   <li><b>ignoreCase</b>: Whether to perform case-insensitive matching (optional, defaults to
 *       false)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Map user roles
 * {"name": "switch", "args": {
 *   "cases": {
 *     "admin": "Administrator",
 *     "user": "Regular User",
 *     "guest": "Guest User"
 *   },
 *   "default": "Unknown Role"
 * }}
 *
 * // Case-insensitive status mapping
 * {"name": "switch", "args": {
 *   "cases": {
 *     "active": "User is active",
 *     "inactive": "User is inactive",
 *     "pending": "User is pending"
 *   },
 *   "ignoreCase": true,
 *   "default": "Status unknown"
 * }}
 *
 * // Simple value mapping without default
 * {"name": "switch", "args": {
 *   "cases": {
 *     "1": "One",
 *     "2": "Two",
 *     "3": "Three"
 *   }
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: "admin", cases: {"admin": "Administrator"} → "Administrator"
 *   <li>Input: "ADMIN", cases: {"admin": "Administrator"}, ignoreCase: true → "Administrator"
 *   <li>Input: "unknown", cases: {"admin": "Administrator"}, default: "User" → "User"
 *   <li>Input: "guest", cases: {"admin": "Administrator"} (no default) → "guest"
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>User role-based claim transformation
 *   <li>Status code to message mapping
 *   <li>Enum value translation
 *   <li>Multi-language content mapping
 *   <li>Category classification
 * </ul>
 */
public class SwitchFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("switch: 'cases' argument is required");
    }

    Object casesObj = args.get("cases");
    if (casesObj == null) {
      throw new IllegalArgumentException("switch: 'cases' argument is required");
    }

    if (!(casesObj instanceof Map)) {
      throw new IllegalArgumentException("switch: 'cases' must be a Map");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> cases = (Map<String, Object>) casesObj;

    boolean ignoreCase = getBooleanArg(args, "ignoreCase", false);

    if (input == null) {
      try {
        if (cases.containsKey(null)) {
          return cases.get(null);
        }
      } catch (NullPointerException e) {
        // Map.of() doesn't allow null keys and throws NPE on containsKey(null)
        // This is expected, so we just continue to default handling
      }
      return args.containsKey("default") ? args.get("default") : null;
    }

    Object defaultValue = args.containsKey("default") ? args.get("default") : input;

    String inputStr = input.toString();

    // Direct match first (most efficient)
    if (cases.containsKey(inputStr)) {
      return cases.get(inputStr);
    }

    // Case-insensitive match if requested
    if (ignoreCase) {
      for (Map.Entry<String, Object> entry : cases.entrySet()) {
        String caseKey = entry.getKey();
        if (caseKey != null && caseKey.equalsIgnoreCase(inputStr)) {
          return entry.getValue();
        }
      }
    }

    return defaultValue;
  }

  @Override
  public String name() {
    return "switch";
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
