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
 * {@code FilterFunction} filters elements from a collection based on specified conditions.
 *
 * <p>This function takes a collection input and returns a new collection containing only elements
 * that satisfy the specified condition. It supports various filtering conditions including null
 * checks, value comparisons, and pattern matching.
 *
 * <p>Arguments:
 *
 * <ul>
 *   <li><b>condition</b>: Condition expression to evaluate for each element (required). Supports:
 *       <ul>
 *         <li>"{{value}} != null" - filter non-null elements
 *         <li>"{{value}} != 'guest'" - filter elements not equal to 'guest'
 *         <li>"{{value}} == 'admin'" - filter elements equal to 'admin'
 *         <li>"{{value}} contains 'role'" - filter elements containing 'role'
 *         <li>"{{value}} startsWith 'prefix'" - filter elements starting with 'prefix'
 *         <li>"{{value}} endsWith 'suffix'" - filter elements ending with 'suffix'
 *         <li>"{{value}} length > 5" - filter elements with length greater than 5
 *       </ul>
 *   <li><b>negate</b>: Whether to negate the condition (optional, defaults to false)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Filter out guest users
 * {"name": "filter", "args": {
 *   "condition": "{{value}} != 'guest'"
 * }}
 *
 * // Filter only admin roles
 * {"name": "filter", "args": {
 *   "condition": "{{value}} == 'admin'"
 * }}
 *
 * // Filter non-empty strings
 * {"name": "filter", "args": {
 *   "condition": "{{value}} != null && {{value}} != ''"
 * }}
 *
 * // Filter strings containing 'role'
 * {"name": "filter", "args": {
 *   "condition": "{{value}} contains 'role'"
 * }}
 * }</pre>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Input: ["admin", "user", "guest"], condition: "{{value}} != 'guest'" → ["admin", "user"]
 *   <li>Input: ["", "hello", null, "world"], condition: "{{value}} != null && {{value}} != ''" →
 *       ["hello", "world"]
 *   <li>Input: ["role_admin", "user", "role_guest"], condition: "{{value}} contains 'role'" →
 *       ["role_admin", "role_guest"]
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>User permission filtering
 *   <li>Data validation and cleanup
 *   <li>Role-based access control filtering
 *   <li>Content filtering by criteria
 * </ul>
 */
public class FilterFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    if (args == null) {
      throw new IllegalArgumentException("filter: 'condition' argument is required");
    }

    String condition = getStringArg(args, "condition");
    if (condition == null || condition.isEmpty()) {
      throw new IllegalArgumentException("filter: 'condition' argument is required");
    }

    boolean negate = getBooleanArg(args, "negate", false);

    Collection<?> collection = toCollection(input);
    if (collection == null) {
      return null;
    }

    List<Object> result = new ArrayList<>();
    for (Object element : collection) {
      try {
        boolean matches = evaluateCondition(element, condition);
        if (negate) {
          matches = !matches;
        }
        if (matches) {
          result.add(element);
        }
      } catch (Exception e) {
        throw new RuntimeException("filter: Error evaluating condition for element: " + element, e);
      }
    }

    return result;
  }

  @Override
  public String name() {
    return "filter";
  }

  /**
   * Evaluates the condition against the element value.
   *
   * @param element the element to test
   * @param condition the condition expression
   * @return true if condition is met, false otherwise
   */
  private boolean evaluateCondition(Object element, String condition) {
    return evaluateBooleanExpression(element, condition.trim());
  }

  /**
   * Evaluates boolean expressions with proper operator precedence and parentheses support. Uses
   * proper parsing instead of string splitting to handle complex expressions correctly.
   *
   * @param element the element to test
   * @param expression the boolean expression
   * @return evaluation result
   */
  private boolean evaluateBooleanExpression(Object element, String expression) {
    String trimmed = expression.trim();

    // Validate syntax before parsing
    validateBooleanExpressionSyntax(trimmed);

    return parseOrExpression(element, trimmed);
  }

  /** Validates boolean expression syntax to catch common errors. */
  private void validateBooleanExpressionSyntax(String expression) {
    // Check for empty expression
    if (expression.isEmpty()) {
      throw new IllegalArgumentException("filter: Empty condition expression");
    }

    // Check for empty parentheses
    if (expression.contains("()")) {
      throw new IllegalArgumentException("filter: Invalid condition - empty parentheses");
    }

    // Check for trailing operators
    if (expression.endsWith("&&") || expression.endsWith("||")) {
      throw new IllegalArgumentException(
          "filter: Invalid condition - expression ends with operator");
    }

    // Check for trailing operators with spaces
    if (expression.endsWith(" &&") || expression.endsWith(" ||")) {
      throw new IllegalArgumentException(
          "filter: Invalid condition - expression ends with operator");
    }

    // Check for leading operators
    if (expression.startsWith("&&") || expression.startsWith("||")) {
      throw new IllegalArgumentException(
          "filter: Invalid condition - expression starts with operator");
    }

    // Check for leading operators with spaces
    if (expression.startsWith("&& ") || expression.startsWith("|| ")) {
      throw new IllegalArgumentException(
          "filter: Invalid condition - expression starts with operator");
    }

    // Check for consecutive operators
    if (expression.contains("&&&&")
        || expression.contains("||||")
        || expression.contains("&&||")
        || expression.contains("||&&")) {
      throw new IllegalArgumentException("filter: Invalid condition - consecutive operators");
    }

    // Check for operators with too many spaces
    if (expression.contains("& &") || expression.contains("| |")) {
      throw new IllegalArgumentException("filter: Invalid condition - malformed operator");
    }
  }

  /** Parses OR expressions (lowest precedence). Format: andExpr ( '||' andExpr )* */
  private boolean parseOrExpression(Object element, String expression) {
    int pos = 0;
    boolean result = false;

    while (pos < expression.length()) {
      // Find the next OR operator that's not inside parentheses (with or without spaces)
      int orPos = findOperatorWithVariableSpacing(expression, "||", pos);

      String andExpr;
      if (orPos == -1) {
        andExpr = expression.substring(pos).trim();
        pos = expression.length();
      } else {
        andExpr = expression.substring(pos, orPos).trim();
        // Skip the operator and any following whitespace
        pos = skipOperator(expression, orPos, "||");
      }

      if (parseAndExpression(element, andExpr)) {
        result = true;
        // Continue parsing to validate syntax, but we already know result is true
      }

      if (orPos == -1) break;
    }

    return result;
  }

  /**
   * Parses AND expressions (higher precedence than OR). Format: primaryExpr ( '&&' primaryExpr )*
   */
  private boolean parseAndExpression(Object element, String expression) {
    int pos = 0;
    boolean result = true;

    while (pos < expression.length()) {
      // Find the next AND operator that's not inside parentheses (with or without spaces)
      int andPos = findOperatorWithVariableSpacing(expression, "&&", pos);

      String primaryExpr;
      if (andPos == -1) {
        primaryExpr = expression.substring(pos).trim();
        pos = expression.length();
      } else {
        primaryExpr = expression.substring(pos, andPos).trim();
        // Skip the operator and any following whitespace
        pos = skipOperator(expression, andPos, "&&");
      }

      if (!parsePrimaryExpression(element, primaryExpr)) {
        result = false;
        // Continue parsing to validate syntax, but we already know result is false
      }

      if (andPos == -1) break;
    }

    return result;
  }

  /** Parses primary expressions (parentheses, boolean literals, or simple conditions). */
  private boolean parsePrimaryExpression(Object element, String expression) {
    String trimmed = expression.trim();

    // Check for any unmatched parentheses in the expression
    if (trimmed.contains("(") || trimmed.contains(")")) {
      if (!isValidParentheses(trimmed)) {
        throw new IllegalArgumentException("filter: Unmatched parentheses in condition");
      }

      // Handle parentheses if they wrap the entire expression
      if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        return parseOrExpression(element, inner);
      }
    }

    return evaluateSimpleCondition(element, trimmed);
  }

  /** Finds an operator outside of parentheses. */
  private int findOperatorOutsideParentheses(String expression, String operator, int startPos) {
    int parenDepth = 0;

    for (int i = startPos; i <= expression.length() - operator.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        parenDepth++;
      } else if (c == ')') {
        parenDepth--;
      } else if (parenDepth == 0
          && expression.substring(i, i + operator.length()).equals(operator)) {
        return i;
      }
    }

    return -1;
  }

  /** Validates that parentheses are properly matched. */
  private boolean isValidParentheses(String expression) {
    int depth = 0;
    for (char c : expression.toCharArray()) {
      if (c == '(') depth++;
      else if (c == ')') depth--;
      if (depth < 0) return false;
    }
    return depth == 0;
  }

  /**
   * Finds an operator with variable spacing (supports operators with or without spaces). For
   * example, finds both "||" and " || " patterns. Correctly handles operators inside quoted strings
   * (ignores them).
   */
  private int findOperatorWithVariableSpacing(String expression, String operator, int startPos) {
    int parenDepth = 0;
    boolean insideQuotes = false;

    for (int i = startPos; i <= expression.length() - operator.length(); i++) {
      char c = expression.charAt(i);

      // Track single quotes to avoid operators inside string literals
      if (c == '\'' && (i == 0 || expression.charAt(i - 1) != '\\')) {
        insideQuotes = !insideQuotes;
      } else if (!insideQuotes) {
        if (c == '(') {
          parenDepth++;
        } else if (c == ')') {
          parenDepth--;
        } else if (parenDepth == 0) {
          // Try to match operator at current position
          if (matchesOperatorAtPosition(expression, operator, i)) {
            return i;
          }

          // Also try to match with spaces around operator
          if (i > 0 && i < expression.length() - operator.length() - 1) {
            // Check for " || " pattern when looking for "||"
            String spacedOperator = " " + operator + " ";
            if (i <= expression.length() - spacedOperator.length()
                && expression.substring(i, i + spacedOperator.length()).equals(spacedOperator)) {
              return i + 1; // Return position of actual operator, not the space
            }
          }
        }
      }
    }

    return -1;
  }

  /** Checks if the operator matches at the given position, handling various spacing patterns. */
  private boolean matchesOperatorAtPosition(String expression, String operator, int pos) {
    // Direct match (e.g., "||")
    if (pos <= expression.length() - operator.length()
        && expression.substring(pos, pos + operator.length()).equals(operator)) {
      return true;
    }

    return false;
  }

  /** Skips past an operator and any following whitespace to position at next expression. */
  private int skipOperator(String expression, int operatorPos, String operator) {
    int pos = operatorPos;

    // First check if we're at a spaced operator pattern like " || "
    if (operatorPos > 0 && expression.charAt(operatorPos - 1) == ' ') {
      // We're at the operator part of " || ", so operatorPos points to "||"
      pos = operatorPos + operator.length();
    } else {
      // We're at just the operator "||", skip it
      pos = operatorPos + operator.length();
    }

    // Skip any trailing whitespace
    while (pos < expression.length() && Character.isWhitespace(expression.charAt(pos))) {
      pos++;
    }

    return pos;
  }

  /**
   * Evaluates a simple condition (no boolean operators).
   *
   * @param element the element to test
   * @param condition the simple condition
   * @return evaluation result
   */
  private boolean evaluateSimpleCondition(Object element, String condition) {
    String trimmed = condition.trim();

    // Handle boolean literals from parentheses evaluation
    if ("true".equals(trimmed)) {
      return true;
    }
    if ("false".equals(trimmed)) {
      return false;
    }

    // Simple conditions - replace {{value}} and evaluate
    String conditionStr = trimmed.replace("{{value}}", elementToString(element));

    // Handle null comparisons
    if (conditionStr.contains("!= null")) {
      return element != null;
    }
    if (conditionStr.contains("== null")) {
      return element == null;
    }

    if (element == null) {
      return false;
    }

    String elementStr = element.toString();

    // String operations
    if (conditionStr.contains(" contains '")) {
      String searchValue = extractQuotedValue(conditionStr, " contains '");
      return elementStr.contains(searchValue);
    }

    if (conditionStr.contains(" startsWith '")) {
      String prefixValue = extractQuotedValue(conditionStr, " startsWith '");
      return elementStr.startsWith(prefixValue);
    }

    if (conditionStr.contains(" endsWith '")) {
      String suffixValue = extractQuotedValue(conditionStr, " endsWith '");
      return elementStr.endsWith(suffixValue);
    }

    // Equality comparisons
    if (conditionStr.contains(" != '")) {
      String compareValue = extractQuotedValue(conditionStr, " != '");
      return !elementStr.equals(compareValue);
    }

    if (conditionStr.contains(" == '")) {
      String compareValue = extractQuotedValue(conditionStr, " == '");
      return elementStr.equals(compareValue);
    }

    // Length comparisons
    if (conditionStr.contains(" length > ")) {
      int compareLength = extractNumericValue(conditionStr, " length > ");
      return elementStr.length() > compareLength;
    }

    if (conditionStr.contains(" length < ")) {
      int compareLength = extractNumericValue(conditionStr, " length < ");
      return elementStr.length() < compareLength;
    }

    if (conditionStr.contains(" length == ")) {
      int compareLength = extractNumericValue(conditionStr, " length == ");
      return elementStr.length() == compareLength;
    }

    // Empty string check
    if (conditionStr.contains(" != ''")) {
      return !elementStr.isEmpty();
    }

    if (conditionStr.contains(" == ''")) {
      return elementStr.isEmpty();
    }

    throw new IllegalArgumentException("filter: Unsupported condition format: " + condition);
  }

  /** Converts element to string for condition evaluation. */
  private String elementToString(Object element) {
    return element == null ? "null" : element.toString();
  }

  /** Extracts quoted value from condition string. */
  private String extractQuotedValue(String condition, String operator) {
    int startIndex = condition.indexOf(operator) + operator.length();
    int endIndex = condition.indexOf("'", startIndex);
    if (endIndex == -1) {
      throw new IllegalArgumentException("filter: Malformed condition - missing closing quote");
    }
    return condition.substring(startIndex, endIndex);
  }

  /** Extracts numeric value from condition string. */
  private int extractNumericValue(String condition, String operator) {
    int startIndex = condition.indexOf(operator) + operator.length();
    String numStr = condition.substring(startIndex).trim();
    try {
      return Integer.parseInt(numStr);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("filter: Invalid numeric value in condition: " + numStr);
    }
  }

  /** Converts input to a Collection. */
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

  /** Helper method to extract string argument. */
  private static String getStringArg(Map<String, Object> args, String key) {
    if (args == null) return null;
    Object value = args.get(key);
    return value != null ? value.toString() : null;
  }

  /** Helper method to extract boolean argument with default value. */
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
