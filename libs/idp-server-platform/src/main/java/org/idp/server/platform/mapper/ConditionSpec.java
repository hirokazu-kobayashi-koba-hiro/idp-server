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

package org.idp.server.platform.mapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.condition.ConditionOperationEvaluator;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * {@code ConditionSpec} defines conditional execution criteria for mapping rules.
 *
 * <p>This class provides flexible condition evaluation based on JSONPath values, leveraging the
 * platform's {@link ConditionOperationEvaluator} for robust condition processing.
 *
 * <p><b>Configurable System Properties:</b>
 *
 * <ul>
 *   <li><b>idp.condition.maxDepth</b>: Maximum compound condition nesting depth (default: 10)
 * </ul>
 *
 * <p>Supported condition operations (via {@link ConditionOperation}):
 *
 * <ul>
 *   <li><b>eq</b>: Equality check
 *   <li><b>ne</b>: Not equal check
 *   <li><b>gt</b>: Greater than (numeric)
 *   <li><b>gte</b>: Greater than or equal (numeric)
 *   <li><b>lt</b>: Less than (numeric)
 *   <li><b>lte</b>: Less than or equal (numeric)
 *   <li><b>in</b>: Value is in collection
 *   <li><b>nin</b>: Value is not in collection
 *   <li><b>exists</b>: Check if JSONPath value exists and is not null
 *   <li><b>missing</b>: Check if JSONPath value is null or missing
 *   <li><b>contains</b>: Collection/string contains value
 *   <li><b>regex</b>: String pattern matching using regular expressions
 *   <li><b>allOf</b>: All nested conditions must be true (logical AND)
 *   <li><b>anyOf</b>: At least one nested condition must be true (logical OR)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Execute mapping only if user is verified
 * new ConditionSpec("exists", "$.user.verified");
 *
 * // Execute mapping only for admin users
 * new ConditionSpec("eq", "$.user.role", "admin");
 *
 * // Execute mapping for users over 18
 * new ConditionSpec("gte", "$.user.age", 18);
 *
 * // Execute mapping for valid email addresses
 * new ConditionSpec("regex", "$.user.email", "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
 *
 * // Compound conditions: admin AND verified
 * new ConditionSpec("allOf", null, List.of(
 *     new ConditionSpec("eq", "$.user.role", "admin"),
 *     new ConditionSpec("exists", "$.user.verified")
 * ));
 *
 * // Compound conditions: admin OR editor
 * new ConditionSpec("anyOf", null, List.of(
 *     new ConditionSpec("eq", "$.user.role", "admin"),
 *     new ConditionSpec("eq", "$.user.role", "editor")
 * ));
 * }</pre>
 */
public class ConditionSpec implements JsonReadable {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ConditionSpec.class);

  // Configurable depth limit for operational flexibility
  private static final int MAX_CONDITION_DEPTH =
      Integer.parseInt(System.getProperty("idp.condition.maxDepth", "10"));

  String operation;
  String path;
  Object value;

  public ConditionSpec() {}

  public ConditionSpec(String operation, String path) {
    this.operation = operation;
    this.path = path;
  }

  public ConditionSpec(String operation, String path, Object value) {
    this.operation = operation;
    this.path = path;
    this.value = value;
  }

  public String operation() {
    return operation;
  }

  public String path() {
    return path;
  }

  public Object value() {
    return value;
  }

  /**
   * Creates a ConditionSpec from a Map representation (typically from JSON deserialization).
   *
   * @param map the map containing condition data
   * @return a new ConditionSpec instance
   * @throws IllegalArgumentException if the map is missing required fields
   */
  public static ConditionSpec fromMap(Map<String, Object> map) {
    if (map == null) {
      throw new IllegalArgumentException("condition map cannot be null");
    }
    String operation = (String) map.get("operation");
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("condition: operation is required");
    }
    String path = (String) map.get("path");
    Object value = map.get("value");
    return new ConditionSpec(operation, path, value);
  }

  /**
   * Evaluates the condition against the provided JSONPath context.
   *
   * <p>Supports both simple conditions (using {@link ConditionOperationEvaluator}) and compound
   * conditions (allOf/anyOf with nested conditions).
   *
   * @param jsonPath the JSONPath wrapper containing the source data
   * @return true if the condition is satisfied, false otherwise
   */
  public boolean evaluate(JsonPathWrapper jsonPath) {
    return evaluate(jsonPath, 0);
  }

  /**
   * Evaluates the condition with depth tracking to prevent stack overflow attacks.
   *
   * @param jsonPath the JSONPath wrapper containing the source data
   * @param depth current recursion depth
   * @return true if the condition is satisfied, false otherwise
   */
  private boolean evaluate(JsonPathWrapper jsonPath, int depth) {
    if (depth > MAX_CONDITION_DEPTH) {
      log.warn(
          "Condition evaluation depth limit exceeded: depth={}, max={}",
          depth,
          MAX_CONDITION_DEPTH);
      return false;
    }
    if (operation == null || operation.isEmpty()) {
      throw new IllegalArgumentException("condition: operation is required");
    }

    try {
      // Handle compound conditions
      if ("allOf".equals(operation)) {
        return evaluateAllOf(jsonPath, depth + 1);
      }
      if ("anyOf".equals(operation)) {
        return evaluateAnyOf(jsonPath, depth + 1);
      }

      // Handle simple conditions - require path
      if (path == null || path.isEmpty()) {
        throw new IllegalArgumentException(
            "condition: path is required for operation '" + operation + "'");
      }

      Object pathValue = jsonPath.readRaw(path);
      return ConditionOperationEvaluator.evaluate(pathValue, operation, value);
    } catch (Exception e) {
      // Mask potential PII in path for security
      String maskedPath = maskSensitivePath(path);
      String errorType = e.getClass().getSimpleName();
      String details = getErrorDetails(operation, value, e);

      log.warn(
          "Condition evaluation failed: operation={}, path={}, errorType={}, details={}, error={}",
          operation,
          maskedPath,
          errorType,
          details,
          e.getMessage());
      return false;
    }
  }

  /** Evaluates "allOf" compound condition - all nested conditions must be true. */
  private boolean evaluateAllOf(JsonPathWrapper jsonPath, int depth) {
    if (!(value instanceof List<?>)) {
      throw new IllegalArgumentException("condition: 'allOf' requires a list of nested conditions");
    }

    @SuppressWarnings("unchecked")
    List<Object> rawConditions = (List<Object>) value;

    if (rawConditions.isEmpty()) {
      return true; // Empty allOf is considered true
    }

    for (Object rawCondition : rawConditions) {
      ConditionSpec condition = convertToConditionSpec(rawCondition);
      if (!condition.evaluate(jsonPath, depth)) {
        return false; // Short-circuit on first false
      }
    }
    return true;
  }

  /** Evaluates "anyOf" compound condition - at least one nested condition must be true. */
  private boolean evaluateAnyOf(JsonPathWrapper jsonPath, int depth) {
    if (!(value instanceof List<?>)) {
      throw new IllegalArgumentException("condition: 'anyOf' requires a list of nested conditions");
    }

    @SuppressWarnings("unchecked")
    List<Object> rawConditions = (List<Object>) value;

    if (rawConditions.isEmpty()) {
      return false; // Empty anyOf is considered false
    }

    for (Object rawCondition : rawConditions) {
      ConditionSpec condition = convertToConditionSpec(rawCondition);
      if (condition.evaluate(jsonPath, depth)) {
        return true; // Short-circuit on first true
      }
    }
    return false;
  }

  /**
   * Converts a raw object (from JSON deserialization) to ConditionSpec.
   *
   * @param rawCondition the raw condition object
   * @return a ConditionSpec instance
   * @throws IllegalArgumentException if the object cannot be converted
   */
  private ConditionSpec convertToConditionSpec(Object rawCondition) {
    if (rawCondition instanceof ConditionSpec) {
      return (ConditionSpec) rawCondition;
    } else if (rawCondition instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> conditionMap = (Map<String, Object>) rawCondition;
      return ConditionSpec.fromMap(conditionMap);
    } else {
      throw new IllegalArgumentException(
          "Invalid condition format: expected ConditionSpec or Map, got "
              + (rawCondition != null ? rawCondition.getClass().getSimpleName() : "null"));
    }
  }

  /**
   * Provides specific error details based on operation type and exception.
   *
   * @param operation the operation that failed
   * @param value the value used in the operation
   * @param exception the exception that occurred
   * @return specific error details for logging
   */
  private String getErrorDetails(String operation, Object value, Exception exception) {
    if (exception.getMessage() != null && exception.getMessage().contains("JSONPath")) {
      return "invalid_jsonpath_syntax";
    }

    switch (operation) {
      case "regex":
        if (exception instanceof IllegalArgumentException) {
          if (exception.getMessage().contains("too long")) {
            return "regex_pattern_too_long";
          }
          return "invalid_regex_pattern";
        }
        break;
      case "gt", "gte", "lt", "lte":
        if (exception.getMessage() != null && exception.getMessage().contains("numeric")) {
          return "non_numeric_comparison";
        }
        break;
      case "allOf", "anyOf":
        if (exception instanceof IllegalArgumentException) {
          return "invalid_compound_condition_structure";
        }
        break;
      case "in", "nin":
        if (value != null && !(value instanceof Collection)) {
          return "expected_collection_for_in_operation";
        }
        break;
      case "contains":
        return "unsupported_contains_operation";
      default:
        break;
    }

    return "unknown_error";
  }

  /**
   * Masks potentially sensitive information in JSONPath for logging.
   *
   * @param path the JSONPath to mask
   * @return masked path for safe logging
   */
  private String maskSensitivePath(String path) {
    if (path == null) {
      return null;
    }

    // Mask common PII fields
    return path.replaceAll("(email|phone|ssn|password|token|key)", "***")
        .replaceAll("\\d{3,}", "***"); // Mask sequences of 3+ digits
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("operation", operation);
    map.put("path", path);
    map.put("value", value);
    return map;
  }
}
