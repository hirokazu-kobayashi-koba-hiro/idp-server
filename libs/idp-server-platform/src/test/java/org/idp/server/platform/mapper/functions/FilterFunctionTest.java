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

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilterFunctionTest {

  private FilterFunction function;

  @BeforeEach
  void setUp() {
    function = new FilterFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("filter", function.name());
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), null));
      assertEquals("filter: 'condition' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenConditionIsMissing() {
      Map<String, Object> args = Map.of("other", "value");
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), args));
      assertEquals("filter: 'condition' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenConditionIsEmpty() {
      Map<String, Object> args = Map.of("condition", "");
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), args));
      assertEquals("filter: 'condition' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenConditionIsUnsupported() {
      Map<String, Object> args = Map.of("condition", "{{value}} unsupported_operator 'test'");
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> function.apply(List.of("a", "b"), args));
      assertTrue(exception.getMessage().contains("filter: Error evaluating condition for element"));
    }
  }

  @Nested
  @DisplayName("Null Handling Tests")
  class NullHandlingTests {

    @Test
    void apply_returnsNull_whenInputIsNull() {
      Map<String, Object> args = Map.of("condition", "{{value}} != null");
      Object result = function.apply(null, args);
      assertNull(result);
    }

    @Test
    void apply_filtersNullValues() {
      List<String> input = Arrays.asList("admin", null, "user", null);
      Map<String, Object> args = Map.of("condition", "{{value}} != null");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_selectsNullValues() {
      List<String> input = Arrays.asList("admin", null, "user", null);
      Map<String, Object> args = Map.of("condition", "{{value}} == null");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(Arrays.asList(null, null), result);
    }

    @Test
    void apply_handlesNullWithOtherConditions() {
      List<String> input = Arrays.asList("admin", null, "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} != 'guest'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin"), result);
    }
  }

  @Nested
  @DisplayName("String Comparison Tests")
  class StringComparisonTests {

    @Test
    void apply_filtersWithNotEquals() {
      List<String> input = List.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} != 'guest'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_filtersWithEquals() {
      List<String> input = List.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} == 'admin'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin"), result);
    }

    @Test
    void apply_filtersWithContains() {
      List<String> input = List.of("role_admin", "user", "role_guest");
      Map<String, Object> args = Map.of("condition", "{{value}} contains 'role'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("role_admin", "role_guest"), result);
    }

    @Test
    void apply_filtersWithStartsWith() {
      List<String> input = List.of("admin_user", "user_admin", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} startsWith 'admin'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin_user"), result);
    }

    @Test
    void apply_filtersWithEndsWith() {
      List<String> input = List.of("admin_user", "user_admin", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} endsWith 'admin'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("user_admin"), result);
    }

    @Test
    void apply_filtersEmptyStrings() {
      List<String> input = Arrays.asList("admin", "", "user", "");
      Map<String, Object> args = Map.of("condition", "{{value}} != ''");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_selectsEmptyStrings() {
      List<String> input = Arrays.asList("admin", "", "user", "");
      Map<String, Object> args = Map.of("condition", "{{value}} == ''");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(Arrays.asList("", ""), result);
    }
  }

  @Nested
  @DisplayName("Length Comparison Tests")
  class LengthComparisonTests {

    @Test
    void apply_filtersWithLengthGreaterThan() {
      List<String> input = List.of("a", "ab", "abc", "abcd");
      Map<String, Object> args = Map.of("condition", "{{value}} length > 2");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("abc", "abcd"), result);
    }

    @Test
    void apply_filtersWithLengthLessThan() {
      List<String> input = List.of("a", "ab", "abc", "abcd");
      Map<String, Object> args = Map.of("condition", "{{value}} length < 3");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("a", "ab"), result);
    }

    @Test
    void apply_filtersWithLengthEquals() {
      List<String> input = List.of("a", "ab", "abc", "abcd");
      Map<String, Object> args = Map.of("condition", "{{value}} length == 2");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("ab"), result);
    }
  }

  @Nested
  @DisplayName("Complex Condition Tests")
  class ComplexConditionTests {

    @Test
    void apply_filtersWithAndCondition() {
      List<String> input = Arrays.asList("admin", "", null, "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} != null && {{value}} != ''");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_filtersWithOrCondition() {
      List<String> input = Arrays.asList("admin", "", null, "user");
      Map<String, Object> args = Map.of("condition", "{{value}} == null || {{value}} == ''");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(Arrays.asList("", null), result);
    }

    @Test
    void apply_handlesComplexAndCondition() {
      List<String> input = List.of("admin", "user", "a", "guest");
      Map<String, Object> args =
          Map.of("condition", "{{value}} != 'guest' && {{value}} length > 1");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_handlesMixedOperatorsPrecedence() {
      // Test: (value == 'a' && value length > 2) || (value == 'b')
      // Should match 'b' due to OR, but not 'a' due to length constraint
      List<String> input = List.of("a", "b", "admin");
      Map<String, Object> args =
          Map.of("condition", "{{value}} == 'a' && {{value}} length > 2 || {{value}} == 'b'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("b"), result);
    }

    @Test
    void apply_handlesComplexMixedOperators() {
      // Test: (value == 'admin' || value == 'user') && (value length > 3)
      List<String> input = List.of("admin", "user", "abc", "guest");
      Map<String, Object> args =
          Map.of(
              "condition", "{{value}} == 'admin' || {{value}} == 'user' && {{value}} length > 3");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      // Due to precedence: admin || (user && length > 3)
      // Should match: admin (always true in OR), user (meets both conditions)
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_handlesOrWithMultipleAndGroups() {
      // Test: (value == 'a' && value length == 1) || (value == 'admin' && value length > 4)
      List<String> input = List.of("a", "admin", "guest", "x");
      Map<String, Object> args =
          Map.of(
              "condition",
              "{{value}} == 'a' && {{value}} length == 1 || {{value}} == 'admin' && {{value}} length > 4");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("a", "admin"), result);
    }

    @Test
    void apply_handlesParenthesesGrouping() {
      // Test: (value == 'a' || value == 'b') && value length == 1
      List<String> input = List.of("a", "b", "ab", "c");
      Map<String, Object> args =
          Map.of("condition", "({{value}} == 'a' || {{value}} == 'b') && {{value}} length == 1");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("a", "b"), result);
    }

    @Test
    void apply_handlesNestedParentheses() {
      // Test: ((value == 'a' || value == 'b') && value length == 1) || value == 'admin'
      List<String> input = List.of("a", "b", "ab", "admin", "c");
      Map<String, Object> args =
          Map.of(
              "condition",
              "(({{value}} == 'a' || {{value}} == 'b') && {{value}} length == 1) || {{value}} == 'admin'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("a", "b", "admin"), result);
    }

    @Test
    void apply_handlesComplexParenthesesMixedOperators() {
      // Test: value == 'admin' || (value == 'user' && value length > 3)
      List<String> input = List.of("admin", "user", "abc", "guest");
      Map<String, Object> args =
          Map.of(
              "condition", "{{value}} == 'admin' || ({{value}} == 'user' && {{value}} length > 3)");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_throwsException_whenUnmatchedParentheses() {
      List<String> input = List.of("a", "b");
      Map<String, Object> args = Map.of("condition", "{{value}} == 'a' && ({{value}} length > 0");

      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> function.apply(input, args));
      assertTrue(exception.getMessage().contains("filter: Error evaluating condition for element"));
    }

    @Test
    void apply_criticalPrecedenceTest_AndOrVsOrAnd() {
      // CRITICAL: Test A && B || C vs A || B && C precedence
      // A && B || C should be evaluated as (A && B) || C
      // A || B && C should be evaluated as A || (B && C)
      List<String> input = List.of("a", "b", "c");

      // Test case 1: value == 'a' && value length > 2 || value == 'b'
      // Should match 'b' because: (false && false) || true = true
      Map<String, Object> args1 =
          Map.of("condition", "{{value}} == 'a' && {{value}} length > 2 || {{value}} == 'b'");
      @SuppressWarnings("unchecked")
      List<Object> result1 = (List<Object>) function.apply(input, args1);
      assertEquals(List.of("b"), result1, "A && B || C precedence failed");

      // Test case 2: value == 'a' || value length > 2 && value == 'c'
      // Should match 'a' because: true || (false && true) = true
      Map<String, Object> args2 =
          Map.of("condition", "{{value}} == 'a' || {{value}} length > 2 && {{value}} == 'c'");
      @SuppressWarnings("unchecked")
      List<Object> result2 = (List<Object>) function.apply(input, args2);
      assertEquals(List.of("a"), result2, "A || B && C precedence failed");
    }

    @Test
    void apply_criticalNestedParentheses() {
      List<String> input = List.of("admin", "user", "a", "b");

      // Test: (value == 'admin' && (value length > 2 || value == 'a'))
      Map<String, Object> args1 =
          Map.of("condition", "{{value}} == 'admin' && ({{value}} length > 2 || {{value}} == 'a')");
      @SuppressWarnings("unchecked")
      List<Object> result1 = (List<Object>) function.apply(input, args1);
      assertEquals(List.of("admin"), result1);

      // Test: ((value == 'admin' || value == 'user') && value length > 1)
      Map<String, Object> args2 =
          Map.of(
              "condition",
              "(({{value}} == 'admin' || {{value}} == 'user') && {{value}} length > 1)");
      @SuppressWarnings("unchecked")
      List<Object> result2 = (List<Object>) function.apply(input, args2);
      assertEquals(List.of("admin", "user"), result2);
    }

    @Test
    void apply_criticalWhitespaceVariations() {
      List<String> input = List.of("a", "b", "c");

      // Test various whitespace patterns should all work the same
      String[] conditions = {
        "{{value}} == 'a'||{{value}} == 'b'&&{{value}} length == 1",
        "{{value}} == 'a' || {{value}} == 'b' && {{value}} length == 1",
        "{{value}} == 'a'|| ({{value}} == 'b'&&{{value}} length == 1)",
        "{{value}} == 'a' ||( {{value}} == 'b' && {{value}} length == 1 )"
      };

      for (String condition : conditions) {
        Map<String, Object> args = Map.of("condition", condition);
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) function.apply(input, args);
        assertEquals(List.of("a", "b"), result, "Whitespace variation failed: " + condition);
      }
    }

    @Test
    void apply_criticalInvalidSyntax() {
      List<String> input = List.of("a", "b");

      // Test trailing operators
      assertInvalidCondition(input, "{{value}} == 'a' &&");
      assertInvalidCondition(input, "{{value}} == 'a' ||");

      // Test leading operators
      assertInvalidCondition(input, "&& {{value}} == 'a'");
      assertInvalidCondition(input, "|| {{value}} == 'a'");

      // Test double operators
      assertInvalidCondition(input, "{{value}} == 'a' && && {{value}} == 'b'");
      assertInvalidCondition(input, "{{value}} == 'a' || || {{value}} == 'b'");

      // Test empty expressions
      assertInvalidCondition(input, "");
      assertInvalidCondition(input, "()");
      assertInvalidCondition(input, "{{value}} == 'a' && ()");
    }

    @Test
    void apply_criticalStringLiteralOperatorDetection() {
      // CRITICAL: This test exposes a major bug in current implementation
      // String literals containing || or && should NOT be treated as operators
      List<String> input = List.of("a||b", "a&&b", "normal");

      // Test: value contains 'a||b' should match "a||b" string, not be parsed as boolean
      Map<String, Object> args = Map.of("condition", "{{value}} contains 'a||b'");
      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);
      assertEquals(List.of("a||b"), result, "String literal operator detection failed");

      // Test: value == 'a&&b' should match "a&&b" string exactly
      Map<String, Object> args2 = Map.of("condition", "{{value}} == 'a&&b'");
      @SuppressWarnings("unchecked")
      List<Object> result2 = (List<Object>) function.apply(input, args2);
      assertEquals(List.of("a&&b"), result2, "String literal with && failed");
    }

    private void assertInvalidCondition(List<String> input, String condition) {
      Map<String, Object> args = Map.of("condition", condition);
      assertThrows(
          Exception.class,
          () -> function.apply(input, args),
          "Should throw exception for invalid condition: " + condition);
    }
  }

  @Nested
  @DisplayName("Negate Option Tests")
  class NegateOptionTests {

    @Test
    void apply_negatesCondition() {
      List<String> input = List.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} == 'admin'", "negate", true);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("user", "guest"), result);
    }

    @Test
    void apply_respectsNegateAsFalse() {
      List<String> input = List.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} == 'admin'", "negate", false);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin"), result);
    }

    @Test
    void apply_handlesNegateAsString() {
      List<String> input = List.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} == 'admin'", "negate", "true");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("user", "guest"), result);
    }
  }

  @Nested
  @DisplayName("Collection Type Support")
  class CollectionTypeSupportTests {

    @Test
    void apply_handlesSet() {
      Set<String> input = Set.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} != 'guest'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertTrue(result.containsAll(List.of("admin", "user")));
      assertEquals(2, result.size());
    }

    @Test
    void apply_handlesArray() {
      String[] input = {"admin", "user", "guest"};
      Map<String, Object> args = Map.of("condition", "{{value}} != 'guest'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_handlesIntArray() {
      int[] input = {1, 2, 3, 4, 5};
      Map<String, Object> args = Map.of("condition", "{{value}} length == 1");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of(1, 2, 3, 4, 5), result);
    }

    @Test
    void apply_handlesSingleElement() {
      String input = "admin";
      Map<String, Object> args = Map.of("condition", "{{value}} == 'admin'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin"), result);
    }

    @Test
    void apply_handlesEmptyCollection() {
      List<String> input = List.of();
      Map<String, Object> args = Map.of("condition", "{{value}} != null");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    void apply_throwsException_whenConditionEvaluationFails() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = Map.of("condition", "{{value}} length > invalid");

      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> function.apply(input, args));
      assertTrue(exception.getMessage().contains("filter: Error evaluating condition for element"));
    }

    @Test
    void apply_handlesInvalidQuotedValue() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = Map.of("condition", "{{value}} == 'unclosed");

      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> function.apply(input, args));
      assertTrue(exception.getMessage().contains("filter: Error evaluating condition for element"));
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_filterUserPermissions() {
      List<String> permissions = List.of("read", "write", "admin", "guest");
      Map<String, Object> args = Map.of("condition", "{{value}} != 'guest'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(permissions, args);

      assertEquals(List.of("read", "write", "admin"), result);
    }

    @Test
    void apply_filterByRolePrefix() {
      List<String> roles = List.of("ROLE_ADMIN", "USER_GUEST", "ROLE_USER", "TEMP_ADMIN");
      Map<String, Object> args = Map.of("condition", "{{value}} startsWith 'ROLE_'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(roles, args);

      assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"), result);
    }

    @Test
    void apply_filterValidEmails() {
      List<String> emails = List.of("admin@example.com", "invalid", "user@test.com", "");
      Map<String, Object> args = Map.of("condition", "{{value}} contains '@'");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(emails, args);

      assertEquals(List.of("admin@example.com", "user@test.com"), result);
    }

    @Test
    void apply_filterByMinimumLength() {
      List<String> usernames = List.of("a", "ab", "admin", "user", "x");
      Map<String, Object> args = Map.of("condition", "{{value}} length > 2");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(usernames, args);

      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_dataValidation() {
      List<String> data = Arrays.asList("valid1", null, "", "valid2", "invalid_long_string");
      Map<String, Object> args =
          Map.of("condition", "{{value}} != null && {{value}} != '' && {{value}} length < 10");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(data, args);

      assertEquals(List.of("valid1", "valid2"), result);
    }
  }
}
