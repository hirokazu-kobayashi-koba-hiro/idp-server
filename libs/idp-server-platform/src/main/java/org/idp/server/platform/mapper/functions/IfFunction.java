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
 * {@code IfFunction} provides conditional logic for value transformation based on condition
 * evaluation.
 *
 * <p>This function evaluates a condition and returns either the "then" value if the condition is
 * true, or the "else" value if the condition is false. It supports various condition types
 * including null checks, equality comparisons, and existence checks.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>condition</b>: Condition expression to evaluate (required). Supports:
 *       <ul>
 *         <li>"null" - checks if input is null
 *         <li>"not_null" - checks if input is not null
 *         <li>"empty" - checks if input is empty (null, empty string, or empty collection)
 *         <li>"not_empty" - checks if input is not empty
 *         <li>"equals:value" - checks if input equals the specified value
 *         <li>"not_equals:value" - checks if input does not equal the specified value
 *         <li>"exists" - checks if input exists and is not null
 *       </ul>
 *   <li><b>then</b>: Value to return when condition is true (required)
 *   <li><b>else</b>: Value to return when condition is false (optional, defaults to input)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Check if user status is valid
 * {"name": "if", "args": {
 *   "condition": "not_null",
 *   "then": "Valid user",
 *   "else": "Invalid user"
 * }}
 *
 * // Check equality with specific value
 * {"name": "if", "args": {
 *   "condition": "equals:admin",
 *   "then": "Administrator",
 *   "else": "Regular User"
 * }}
 *
 * // Check if collection is empty
 * {"name": "if", "args": {
 *   "condition": "empty",
 *   "then": "No items",
 *   "else": "Has items"
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: null, condition: "null", then: "empty", else: "not empty" → "empty"
 *   <li>Input: "admin", condition: "equals:admin", then: "Administrator" → "Administrator"
 *   <li>Input: "", condition: "empty", then: "No value", else: "Has value" → "No value"
 *   <li>Input: ["item"], condition: "not_empty", then: "Found", else: "None" → "Found"
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>User role-based claim transformation
 *   <li>Conditional field configuration
 *   <li>Input validation with branching logic
 *   <li>Default value assignment based on conditions
 * </ul>
 */
public class IfFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("if: 'condition' and 'then' arguments are required");
    }

    String condition = getStringArg(args, "condition");
    if (condition == null || condition.isEmpty()) {
      throw new IllegalArgumentException("if: 'condition' argument is required");
    }

    Object thenValue = args.get("then");
    if (thenValue == null) {
      throw new IllegalArgumentException("if: 'then' argument is required");
    }

    Object elseValue = args.containsKey("else") ? args.get("else") : input;

    boolean conditionResult = evaluateCondition(input, condition);
    return conditionResult ? thenValue : elseValue;
  }

  @Override
  public String name() {
    return "if";
  }

  /**
   * Evaluates the condition against the input value.
   *
   * @param input the input value to test
   * @param condition the condition expression
   * @return true if condition is met, false otherwise
   */
  private boolean evaluateCondition(Object input, String condition) {
    switch (condition.toLowerCase()) {
      case "null":
        return input == null;
      case "not_null":
        return input != null;
      case "empty":
        return isEmpty(input);
      case "not_empty":
        return !isEmpty(input);
      case "exists":
        return input != null;
      default:
        if (condition.startsWith("equals:")) {
          String value = condition.substring(7);
          return input != null && input.toString().equals(value);
        }
        if (condition.startsWith("not_equals:")) {
          String value = condition.substring(11);
          return input == null || !input.toString().equals(value);
        }
        throw new IllegalArgumentException(
            "if: Invalid condition '"
                + condition
                + "'. Supported conditions: null, not_null, empty, not_empty, exists, equals:value, not_equals:value");
    }
  }

  /**
   * Checks if the input value is considered empty.
   *
   * @param input the input value to check
   * @return true if input is null, empty string, or empty collection/map
   */
  private boolean isEmpty(Object input) {
    if (input == null) {
      return true;
    }
    if (input instanceof String s) {
      return s.isEmpty();
    }
    if (input instanceof java.util.Collection<?> col) {
      return col.isEmpty();
    }
    if (input instanceof java.util.Map<?, ?> map) {
      return map.isEmpty();
    }
    return false;
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
