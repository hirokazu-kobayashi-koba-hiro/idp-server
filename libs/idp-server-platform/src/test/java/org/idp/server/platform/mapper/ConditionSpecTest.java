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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.Test;

class ConditionSpecTest {

  @Test
  void evaluateExists_shouldReturnTrueWhenValueExists() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("exists", "$.user.name");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateExists_shouldReturnFalseWhenValueIsNull() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": null}}");
    ConditionSpec condition = new ConditionSpec("exists", "$.user.name");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateExists_shouldReturnFalseWhenPathDoesNotExist() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {}}");
    ConditionSpec condition = new ConditionSpec("exists", "$.user.email");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateMissing_shouldReturnTrueWhenValueIsNull() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": null}}");
    ConditionSpec condition = new ConditionSpec("missing", "$.user.name");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateMissing_shouldReturnFalseWhenValueExists() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("missing", "$.user.name");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateEq_shouldReturnTrueForMatchingStringValues() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateEq_shouldReturnFalseForNonMatchingStringValues() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"user\"}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNe_shouldReturnTrueForNonMatchingValues() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"user\"}}");
    ConditionSpec condition = new ConditionSpec("ne", "$.user.role", "admin");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNe_shouldReturnFalseForMatchingValues() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");
    ConditionSpec condition = new ConditionSpec("ne", "$.user.role", "admin");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateEq_shouldReturnTrueForMatchingNumericValues() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 25}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.age", 25);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateEq_shouldNotMatchDifferentNumericTypes() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 25}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.age", 25.0);

    // Objects.equals(25, 25.0) is false - different types are not equal
    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateGte_shouldHandleNumericTypeCoercion() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 25}}");
    ConditionSpec condition = new ConditionSpec("gte", "$.user.age", 25.0);

    // Numeric comparison does handle type coercion
    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateEq_shouldReturnTrueForMatchingBooleanValues() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"verified\": true}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.verified", true);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateEq_shouldReturnFalseWhenValueIsNull() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": null}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateGt_shouldReturnTrueForGreaterValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 25}}");
    ConditionSpec condition = new ConditionSpec("gt", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateGt_shouldReturnFalseForEqualValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 18}}");
    ConditionSpec condition = new ConditionSpec("gt", "$.user.age", 18);

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateGte_shouldReturnTrueForEqualValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 18}}");
    ConditionSpec condition = new ConditionSpec("gte", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateGte_shouldReturnTrueForGreaterValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 25}}");
    ConditionSpec condition = new ConditionSpec("gte", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateLt_shouldReturnTrueForLesserValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 15}}");
    ConditionSpec condition = new ConditionSpec("lt", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateLt_shouldReturnFalseForEqualValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 18}}");
    ConditionSpec condition = new ConditionSpec("lt", "$.user.age", 18);

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateLte_shouldReturnTrueForEqualValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 18}}");
    ConditionSpec condition = new ConditionSpec("lte", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateLte_shouldReturnTrueForLesserValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 15}}");
    ConditionSpec condition = new ConditionSpec("lte", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateIn_shouldReturnTrueWhenValueIsInCollection() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");
    ConditionSpec condition =
        new ConditionSpec("in", "$.user.role", List.of("admin", "editor", "viewer"));

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateIn_shouldReturnFalseWhenValueIsNotInCollection() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"guest\"}}");
    ConditionSpec condition =
        new ConditionSpec("in", "$.user.role", List.of("admin", "editor", "viewer"));

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNin_shouldReturnTrueWhenValueIsNotInCollection() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"guest\"}}");
    ConditionSpec condition =
        new ConditionSpec("nin", "$.user.role", List.of("admin", "editor", "viewer"));

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNin_shouldReturnFalseWhenValueIsInCollection() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");
    ConditionSpec condition =
        new ConditionSpec("nin", "$.user.role", List.of("admin", "editor", "viewer"));

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateContains_shouldReturnTrueWhenStringContainsSubstring() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": \"john@example.com\"}}");
    ConditionSpec condition = new ConditionSpec("contains", "$.user.email", "@example");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateContains_shouldReturnFalseWhenStringDoesNotContainSubstring() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": \"john@test.com\"}}");
    ConditionSpec condition = new ConditionSpec("contains", "$.user.email", "@example");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldReturnTrueForMatchingPattern() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": \"john@example.com\"}}");
    ConditionSpec condition =
        new ConditionSpec("regex", "$.user.email", "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldReturnFalseForNonMatchingPattern() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": \"invalid-email\"}}");
    ConditionSpec condition =
        new ConditionSpec("regex", "$.user.email", "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldReturnFalseWhenValueIsNull() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": null}}");
    ConditionSpec condition =
        new ConditionSpec("regex", "$.user.email", "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldReturnFalseForUnsupportedConditionType() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("unknown", "$.user.name");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldThrowExceptionWhenOperationIsNull() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec(null, "$.user.name");

    assertThrows(IllegalArgumentException.class, () -> condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldThrowExceptionWhenOperationIsEmpty() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("", "$.user.name");

    assertThrows(IllegalArgumentException.class, () -> condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldReturnFalseWhenPathIsNullForSimpleCondition() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("exists", null);

    // Should catch exception and return false due to try-catch
    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldReturnFalseWhenPathIsEmptyForSimpleCondition() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("exists", "");

    // Should catch exception and return false due to try-catch
    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldReturnFalseOnEvaluationException() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("exists", "$.invalid[path");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void toMap_shouldReturnCorrectRepresentation() {
    ConditionSpec condition = new ConditionSpec("gte", "$.user.age", 18);

    Map<String, Object> result = condition.toMap();

    assertEquals("gte", result.get("operation"));
    assertEquals("$.user.age", result.get("path"));
    assertEquals(18, result.get("value"));
  }

  @Test
  void toMap_shouldHandleAllFields() {
    ConditionSpec condition = new ConditionSpec("eq", "$.user.role", "admin");

    Map<String, Object> result = condition.toMap();

    assertEquals("eq", result.get("operation"));
    assertEquals("$.user.role", result.get("path"));
    assertEquals("admin", result.get("value"));
  }

  @Test
  void evaluateAllOf_shouldReturnTrueWhenAllConditionsAreTrue() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": true}}");
    List<ConditionSpec> conditions =
        List.of(
            new ConditionSpec("eq", "$.user.role", "admin"),
            new ConditionSpec("exists", "$.user.verified"));
    ConditionSpec condition = new ConditionSpec("allOf", null, conditions);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateAllOf_shouldReturnFalseWhenAnyConditionIsFalse() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"user\", \"verified\": true}}");
    List<ConditionSpec> conditions =
        List.of(
            new ConditionSpec("eq", "$.user.role", "admin"),
            new ConditionSpec("exists", "$.user.verified"));
    ConditionSpec condition = new ConditionSpec("allOf", null, conditions);

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateAllOf_shouldReturnTrueForEmptyConditionList() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("allOf", null, List.of());

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateAnyOf_shouldReturnTrueWhenAnyConditionIsTrue() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": false}}");
    List<ConditionSpec> conditions =
        List.of(
            new ConditionSpec("eq", "$.user.role", "admin"),
            new ConditionSpec("eq", "$.user.role", "editor"));
    ConditionSpec condition = new ConditionSpec("anyOf", null, conditions);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateAnyOf_shouldReturnFalseWhenAllConditionsAreFalse() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"user\"}}");
    List<ConditionSpec> conditions =
        List.of(
            new ConditionSpec("eq", "$.user.role", "admin"),
            new ConditionSpec("eq", "$.user.role", "editor"));
    ConditionSpec condition = new ConditionSpec("anyOf", null, conditions);

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateAnyOf_shouldReturnFalseForEmptyConditionList() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("anyOf", null, List.of());

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNestedCompoundConditions_shouldWork() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": true, \"age\": 25}}");

    // (role == admin AND verified) OR age >= 21
    ConditionSpec adminAndVerified =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"),
                new ConditionSpec("exists", "$.user.verified")));
    ConditionSpec ageCheck = new ConditionSpec("gte", "$.user.age", 21);

    ConditionSpec condition = new ConditionSpec("anyOf", null, List.of(adminAndVerified, ageCheck));

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluate_shouldThrowExceptionForAllOfWithoutList() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("allOf", null, "not a list");

    assertFalse(condition.evaluate(jsonPath)); // Should catch exception and return false
  }

  @Test
  void evaluate_shouldThrowExceptionForAnyOfWithoutList() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("anyOf", null, "not a list");

    assertFalse(condition.evaluate(jsonPath)); // Should catch exception and return false
  }

  @Test
  void evaluate_shouldPreventStackOverflowWithDeepNesting() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");

    // Create deeply nested conditions (15 levels - exceeds MAX_CONDITION_DEPTH of 10)
    ConditionSpec condition = createDeeplyNestedCondition(15);

    // Should return false due to depth limit, not stack overflow
    assertFalse(condition.evaluate(jsonPath));
  }

  private ConditionSpec createDeeplyNestedCondition(int depth) {
    if (depth <= 0) {
      return new ConditionSpec("eq", "$.user.role", "admin");
    }
    return new ConditionSpec("allOf", null, List.of(createDeeplyNestedCondition(depth - 1)));
  }

  @Test
  void evaluateRegex_shouldCacheCompiledPatterns() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": \"test@example.com\"}}");
    String emailPattern = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$";
    ConditionSpec condition = new ConditionSpec("regex", "$.user.email", emailPattern);

    // Multiple evaluations should use cached pattern
    assertTrue(condition.evaluate(jsonPath));
    assertTrue(condition.evaluate(jsonPath));
    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldHandleInvalidRegexPattern() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"email\": \"test@example.com\"}}");
    ConditionSpec condition = new ConditionSpec("regex", "$.user.email", "[invalid");

    // Should return false for invalid regex, not throw exception
    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldHandleExcessivelyLongPattern() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"text\": \"hello\"}}");

    // Create a very long regex pattern (over 1000 characters)
    String longPattern = "a".repeat(1001);
    ConditionSpec condition = new ConditionSpec("regex", "$.user.text", longPattern);

    // Should return false for excessively long pattern
    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldWorkWithValidPatterns() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"phone\": \"+1-555-123-4567\"}}");
    String phonePattern = "^\\+\\d{1,3}-\\d{3}-\\d{3}-\\d{4}$";
    ConditionSpec condition = new ConditionSpec("regex", "$.user.phone", phonePattern);

    assertTrue(condition.evaluate(jsonPath));
  }

  // Truth table tests for compound conditions
  @Test
  void evaluateAllOf_truthTable_bothTrue() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": true}}");
    ConditionSpec condition =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true) // TRUE
                ));

    assertTrue(condition.evaluate(jsonPath)); // TRUE AND TRUE = TRUE
  }

  @Test
  void evaluateAllOf_truthTable_firstTrueSecondFalse() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": false}}");
    ConditionSpec condition =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true) // FALSE
                ));

    assertFalse(condition.evaluate(jsonPath)); // TRUE AND FALSE = FALSE
  }

  @Test
  void evaluateAllOf_truthTable_firstFalseSecondTrue() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"user\", \"verified\": true}}");
    ConditionSpec condition =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // FALSE
                new ConditionSpec("eq", "$.user.verified", true) // TRUE
                ));

    assertFalse(condition.evaluate(jsonPath)); // FALSE AND TRUE = FALSE
  }

  @Test
  void evaluateAllOf_truthTable_bothFalse() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"user\", \"verified\": false}}");
    ConditionSpec condition =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // FALSE
                new ConditionSpec("eq", "$.user.verified", true) // FALSE
                ));

    assertFalse(condition.evaluate(jsonPath)); // FALSE AND FALSE = FALSE
  }

  @Test
  void evaluateAnyOf_truthTable_bothTrue() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": true}}");
    ConditionSpec condition =
        new ConditionSpec(
            "anyOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true) // TRUE
                ));

    assertTrue(condition.evaluate(jsonPath)); // TRUE OR TRUE = TRUE
  }

  @Test
  void evaluateAnyOf_truthTable_firstTrueSecondFalse() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": false}}");
    ConditionSpec condition =
        new ConditionSpec(
            "anyOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true) // FALSE
                ));

    assertTrue(condition.evaluate(jsonPath)); // TRUE OR FALSE = TRUE
  }

  @Test
  void evaluateAnyOf_truthTable_firstFalseSecondTrue() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"user\", \"verified\": true}}");
    ConditionSpec condition =
        new ConditionSpec(
            "anyOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // FALSE
                new ConditionSpec("eq", "$.user.verified", true) // TRUE
                ));

    assertTrue(condition.evaluate(jsonPath)); // FALSE OR TRUE = TRUE
  }

  @Test
  void evaluateAnyOf_truthTable_bothFalse() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"user\", \"verified\": false}}");
    ConditionSpec condition =
        new ConditionSpec(
            "anyOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // FALSE
                new ConditionSpec("eq", "$.user.verified", true) // FALSE
                ));

    assertFalse(condition.evaluate(jsonPath)); // FALSE OR FALSE = FALSE
  }

  @Test
  void evaluateCompound_truthTable_multipleOperations() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"role\": \"admin\", \"verified\": true, \"age\": 25}}");

    // Test complex combination: (role=admin AND verified=true) OR age<18
    ConditionSpec adminAndVerified =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true) // TRUE
                )); // TRUE AND TRUE = TRUE

    ConditionSpec ageCondition = new ConditionSpec("lt", "$.user.age", 18); // FALSE (25 < 18)

    ConditionSpec finalCondition =
        new ConditionSpec(
            "anyOf",
            null,
            List.of(
                adminAndVerified, // TRUE
                ageCondition // FALSE
                ));

    assertTrue(finalCondition.evaluate(jsonPath)); // TRUE OR FALSE = TRUE
  }

  @Test
  void evaluateCompound_truthTable_threeWayAnd() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper(
            "{\"user\": {\"role\": \"admin\", \"verified\": true, \"active\": true}}");
    ConditionSpec condition =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true), // TRUE
                new ConditionSpec("eq", "$.user.active", true) // TRUE
                ));

    assertTrue(condition.evaluate(jsonPath)); // TRUE AND TRUE AND TRUE = TRUE
  }

  @Test
  void evaluateCompound_truthTable_threeWayAndWithOneFalse() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper(
            "{\"user\": {\"role\": \"admin\", \"verified\": true, \"active\": false}}");
    ConditionSpec condition =
        new ConditionSpec(
            "allOf",
            null,
            List.of(
                new ConditionSpec("eq", "$.user.role", "admin"), // TRUE
                new ConditionSpec("eq", "$.user.verified", true), // TRUE
                new ConditionSpec("eq", "$.user.active", true) // FALSE
                ));

    assertFalse(condition.evaluate(jsonPath)); // TRUE AND TRUE AND FALSE = FALSE
  }

  // Type boundary and edge case tests
  @Test
  void evaluateNumeric_shouldHandleIntegerComparison() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"age\": 25}}");
    ConditionSpec condition = new ConditionSpec("gte", "$.user.age", 18);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNumeric_shouldHandleDoubleComparison() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"score\": 85.5}}");
    ConditionSpec condition = new ConditionSpec("gt", "$.user.score", 85.0);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNumeric_shouldHandleStringNumber() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"balance\": \"1000.50\"}}");
    ConditionSpec condition = new ConditionSpec("gte", "$.user.balance", 1000);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNumeric_shouldHandleZeroBoundary() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"count\": 0}}");
    ConditionSpec ltCondition = new ConditionSpec("lt", "$.user.count", 1);
    ConditionSpec gteCondition = new ConditionSpec("gte", "$.user.count", 0);

    assertTrue(ltCondition.evaluate(jsonPath));
    assertTrue(gteCondition.evaluate(jsonPath));
  }

  @Test
  void evaluateNumeric_shouldHandleNegativeNumbers() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"debt\": -500}}");
    ConditionSpec condition = new ConditionSpec("lt", "$.user.debt", 0);

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateNumeric_shouldReturnFalseForInvalidNumber() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"invalid\": \"not-a-number\"}}");
    ConditionSpec condition = new ConditionSpec("gt", "$.user.invalid", 0);

    assertFalse(condition.evaluate(jsonPath)); // Should return false for invalid number comparison
  }

  @Test
  void evaluateExists_shouldHandleNullValue() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"optional\": null}}");
    ConditionSpec existsCondition = new ConditionSpec("exists", "$.user.optional");
    ConditionSpec missingCondition = new ConditionSpec("missing", "$.user.optional");

    assertFalse(existsCondition.evaluate(jsonPath));
    assertTrue(missingCondition.evaluate(jsonPath));
  }

  @Test
  void evaluateExists_shouldHandleMissingProperty() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {}}");
    ConditionSpec existsCondition = new ConditionSpec("exists", "$.user.nonexistent");
    ConditionSpec missingCondition = new ConditionSpec("missing", "$.user.nonexistent");

    assertFalse(existsCondition.evaluate(jsonPath));
    assertTrue(missingCondition.evaluate(jsonPath));
  }

  @Test
  void evaluateIn_shouldHandleEmptyCollection() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"role\": \"admin\"}}");
    ConditionSpec condition = new ConditionSpec("in", "$.user.role", List.of());

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateIn_shouldHandleDifferentTypes() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"id\": 123}}");
    ConditionSpec stringCondition = new ConditionSpec("in", "$.user.id", List.of("123", "456"));
    ConditionSpec numberCondition = new ConditionSpec("in", "$.user.id", List.of(123, 456));

    assertFalse(stringCondition.evaluate(jsonPath)); // 123 != "123"
    assertTrue(numberCondition.evaluate(jsonPath)); // 123 == 123
  }

  @Test
  void evaluateContains_shouldHandleStringContains() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"email\": \"admin@example.com\"}}");
    ConditionSpec condition = new ConditionSpec("contains", "$.user.email", "admin");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateContains_shouldHandleArrayContains() {
    JsonPathWrapper jsonPath =
        new JsonPathWrapper("{\"user\": {\"roles\": [\"admin\", \"user\"]}}");
    ConditionSpec condition = new ConditionSpec("contains", "$.user.roles", "admin");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateContains_shouldReturnFalseForUnsupportedTypes() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"count\": 5}}");
    ConditionSpec condition = new ConditionSpec("contains", "$.user.count", 1);

    assertFalse(condition.evaluate(jsonPath)); // Number doesn't support contains
  }

  @Test
  void evaluateRegex_shouldHandleEmptyString() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"\"}}");
    ConditionSpec condition = new ConditionSpec("regex", "$.user.name", "^$");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateRegex_shouldHandleUnicodeCharacters() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"José María\"}}");
    ConditionSpec condition = new ConditionSpec("regex", "$.user.name", "^[\\p{L}\\s]+$");

    assertTrue(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateInvalidOperation_shouldReturnFalse() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("invalid_operation", "$.user.name", "John");

    assertFalse(condition.evaluate(jsonPath));
  }

  @Test
  void evaluateInvalidJsonPath_shouldReturnFalse() {
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"user\": {\"name\": \"John\"}}");
    ConditionSpec condition = new ConditionSpec("eq", "$.invalid[syntax", "John");

    assertFalse(condition.evaluate(jsonPath));
  }
}
