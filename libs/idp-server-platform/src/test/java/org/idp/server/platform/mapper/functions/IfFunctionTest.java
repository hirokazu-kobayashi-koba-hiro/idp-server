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

class IfFunctionTest {

  private IfFunction function;

  @BeforeEach
  void setUp() {
    function = new IfFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("if", function.name());
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", null));
      assertEquals("if: 'condition' and 'then' arguments are required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenConditionIsMissing() {
      Map<String, Object> args = Map.of("then", "result");
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("if: 'condition' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenConditionIsEmpty() {
      Map<String, Object> args = Map.of("condition", "", "then", "result");
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("if: 'condition' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenThenIsMissing() {
      Map<String, Object> args = Map.of("condition", "null");
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("if: 'then' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenConditionIsInvalid() {
      Map<String, Object> args = Map.of("condition", "invalid", "then", "result");
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertTrue(exception.getMessage().contains("Invalid condition"));
    }
  }

  @Nested
  @DisplayName("Null Condition Tests")
  class NullConditionTests {

    @Test
    void apply_returnsElse_whenInputIsNotNullAndConditionIsNull() {
      Map<String, Object> args = Map.of("condition", "null", "then", "is null", "else", "not null");
      Object result = function.apply("value", args);
      assertEquals("not null", result);
    }

    @Test
    void apply_returnsThen_whenInputIsNullAndConditionIsNull() {
      Map<String, Object> args = Map.of("condition", "null", "then", "is null", "else", "not null");
      Object result = function.apply(null, args);
      assertEquals("is null", result);
    }

    @Test
    void apply_returnsInput_whenInputIsNotNullAndConditionIsNullAndNoElse() {
      Map<String, Object> args = Map.of("condition", "null", "then", "is null");
      Object result = function.apply("value", args);
      assertEquals("value", result);
    }
  }

  @Nested
  @DisplayName("Not Null Condition Tests")
  class NotNullConditionTests {

    @Test
    void apply_returnsThen_whenInputIsNotNullAndConditionIsNotNull() {
      Map<String, Object> args =
          Map.of("condition", "not_null", "then", "exists", "else", "missing");
      Object result = function.apply("value", args);
      assertEquals("exists", result);
    }

    @Test
    void apply_returnsElse_whenInputIsNullAndConditionIsNotNull() {
      Map<String, Object> args =
          Map.of("condition", "not_null", "then", "exists", "else", "missing");
      Object result = function.apply(null, args);
      assertEquals("missing", result);
    }
  }

  @Nested
  @DisplayName("Empty Condition Tests")
  class EmptyConditionTests {

    @Test
    void apply_returnsThen_whenInputIsNullAndConditionIsEmpty() {
      Map<String, Object> args =
          Map.of("condition", "empty", "then", "is empty", "else", "not empty");
      Object result = function.apply(null, args);
      assertEquals("is empty", result);
    }

    @Test
    void apply_returnsThen_whenInputIsEmptyStringAndConditionIsEmpty() {
      Map<String, Object> args =
          Map.of("condition", "empty", "then", "is empty", "else", "not empty");
      Object result = function.apply("", args);
      assertEquals("is empty", result);
    }

    @Test
    void apply_returnsThen_whenInputIsEmptyListAndConditionIsEmpty() {
      Map<String, Object> args =
          Map.of("condition", "empty", "then", "is empty", "else", "not empty");
      Object result = function.apply(new ArrayList<>(), args);
      assertEquals("is empty", result);
    }

    @Test
    void apply_returnsThen_whenInputIsEmptyMapAndConditionIsEmpty() {
      Map<String, Object> args =
          Map.of("condition", "empty", "then", "is empty", "else", "not empty");
      Object result = function.apply(new HashMap<>(), args);
      assertEquals("is empty", result);
    }

    @Test
    void apply_returnsElse_whenInputIsNonEmptyStringAndConditionIsEmpty() {
      Map<String, Object> args =
          Map.of("condition", "empty", "then", "is empty", "else", "not empty");
      Object result = function.apply("value", args);
      assertEquals("not empty", result);
    }

    @Test
    void apply_returnsElse_whenInputIsNonEmptyListAndConditionIsEmpty() {
      Map<String, Object> args =
          Map.of("condition", "empty", "then", "is empty", "else", "not empty");
      Object result = function.apply(List.of("item"), args);
      assertEquals("not empty", result);
    }
  }

  @Nested
  @DisplayName("Not Empty Condition Tests")
  class NotEmptyConditionTests {

    @Test
    void apply_returnsElse_whenInputIsNullAndConditionIsNotEmpty() {
      Map<String, Object> args =
          Map.of("condition", "not_empty", "then", "has value", "else", "no value");
      Object result = function.apply(null, args);
      assertEquals("no value", result);
    }

    @Test
    void apply_returnsElse_whenInputIsEmptyStringAndConditionIsNotEmpty() {
      Map<String, Object> args =
          Map.of("condition", "not_empty", "then", "has value", "else", "no value");
      Object result = function.apply("", args);
      assertEquals("no value", result);
    }

    @Test
    void apply_returnsThen_whenInputIsNonEmptyStringAndConditionIsNotEmpty() {
      Map<String, Object> args =
          Map.of("condition", "not_empty", "then", "has value", "else", "no value");
      Object result = function.apply("value", args);
      assertEquals("has value", result);
    }
  }

  @Nested
  @DisplayName("Exists Condition Tests")
  class ExistsConditionTests {

    @Test
    void apply_returnsElse_whenInputIsNullAndConditionIsExists() {
      Map<String, Object> args = Map.of("condition", "exists", "then", "exists", "else", "missing");
      Object result = function.apply(null, args);
      assertEquals("missing", result);
    }

    @Test
    void apply_returnsThen_whenInputIsNotNullAndConditionIsExists() {
      Map<String, Object> args = Map.of("condition", "exists", "then", "exists", "else", "missing");
      Object result = function.apply("value", args);
      assertEquals("exists", result);
    }
  }

  @Nested
  @DisplayName("Equals Condition Tests")
  class EqualsConditionTests {

    @Test
    void apply_returnsThen_whenInputEqualsValue() {
      Map<String, Object> args =
          Map.of("condition", "equals:admin", "then", "Administrator", "else", "User");
      Object result = function.apply("admin", args);
      assertEquals("Administrator", result);
    }

    @Test
    void apply_returnsElse_whenInputDoesNotEqualValue() {
      Map<String, Object> args =
          Map.of("condition", "equals:admin", "then", "Administrator", "else", "User");
      Object result = function.apply("user", args);
      assertEquals("User", result);
    }

    @Test
    void apply_returnsElse_whenInputIsNullAndConditionIsEquals() {
      Map<String, Object> args =
          Map.of("condition", "equals:admin", "then", "Administrator", "else", "User");
      Object result = function.apply(null, args);
      assertEquals("User", result);
    }

    @Test
    void apply_returnsThen_whenInputEqualsNumericValue() {
      Map<String, Object> args =
          Map.of("condition", "equals:123", "then", "Found", "else", "Not found");
      Object result = function.apply(123, args);
      assertEquals("Found", result);
    }
  }

  @Nested
  @DisplayName("Not Equals Condition Tests")
  class NotEqualsConditionTests {

    @Test
    void apply_returnsElse_whenInputEqualsValue() {
      Map<String, Object> args =
          Map.of("condition", "not_equals:admin", "then", "Not admin", "else", "Is admin");
      Object result = function.apply("admin", args);
      assertEquals("Is admin", result);
    }

    @Test
    void apply_returnsThen_whenInputDoesNotEqualValue() {
      Map<String, Object> args =
          Map.of("condition", "not_equals:admin", "then", "Not admin", "else", "Is admin");
      Object result = function.apply("user", args);
      assertEquals("Not admin", result);
    }

    @Test
    void apply_returnsThen_whenInputIsNullAndConditionIsNotEquals() {
      Map<String, Object> args =
          Map.of("condition", "not_equals:admin", "then", "Not admin", "else", "Is admin");
      Object result = function.apply(null, args);
      assertEquals("Not admin", result);
    }
  }

  @Nested
  @DisplayName("Case Sensitivity Tests")
  class CaseSensitivityTests {

    @Test
    void apply_isCaseSensitive_forConditionNames() {
      Map<String, Object> args = Map.of("condition", "NULL", "then", "is null", "else", "not null");
      Object result = function.apply(null, args);
      assertEquals("is null", result); // Should work as condition names are normalized to lowercase
    }

    @Test
    void apply_isCaseSensitive_forEqualsValues() {
      Map<String, Object> args =
          Map.of("condition", "equals:Admin", "then", "Found", "else", "Not found");
      Object result = function.apply("admin", args);
      assertEquals("Not found", result); // Case sensitive comparison
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    void apply_handlesComplexObjects() {
      Map<String, Object> complexObject = Map.of("key", "value");
      Map<String, Object> args =
          Map.of("condition", "not_null", "then", "Complex", "else", "Simple");
      Object result = function.apply(complexObject, args);
      assertEquals("Complex", result);
    }

    @Test
    void apply_handlesZeroNumber() {
      Map<String, Object> args =
          Map.of("condition", "equals:0", "then", "Zero", "else", "Not zero");
      Object result = function.apply(0, args);
      assertEquals("Zero", result);
    }

    @Test
    void apply_handlesBooleanInput() {
      Map<String, Object> args =
          Map.of("condition", "equals:true", "then", "True", "else", "False");
      Object result = function.apply(true, args);
      assertEquals("True", result);
    }

    @Test
    void apply_handlesEmptyArgsMap() {
      Map<String, Object> args = new HashMap<>();
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("if: 'condition' argument is required", exception.getMessage());
    }

    @Test
    void apply_preservesNullElseValue() {
      Map<String, Object> args = new HashMap<>();
      args.put("condition", "null");
      args.put("then", "is null");
      args.put("else", null);
      Object result = function.apply("value", args);
      assertNull(result);
    }
  }
}
