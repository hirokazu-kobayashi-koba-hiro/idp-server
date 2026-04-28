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

class PluckFunctionTest {

  private PluckFunction function;

  @BeforeEach
  void setUp() {
    function = new PluckFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("pluck", function.name());
  }

  @Nested
  @DisplayName("Basic Pluck Tests")
  class BasicPluckTests {

    @Test
    void apply_extractsFieldFromObjectArray() {
      List<Map<String, Object>> input =
          List.of(
              Map.of("account_no", "123", "type", "savings"),
              Map.of("account_no", "456", "type", "checking"));
      Map<String, Object> args = Map.of("field", "account_no");
      Object result = function.apply(input, args);
      assertEquals(List.of("123", "456"), result);
    }

    @Test
    void apply_returnsNullWhenInputIsNull() {
      Map<String, Object> args = Map.of("field", "name");
      Object result = function.apply(null, args);
      assertNull(result);
    }

    @Test
    void apply_extractsNumericField() {
      List<Map<String, Object>> input =
          List.of(Map.of("id", 1, "name", "Alice"), Map.of("id", 2, "name", "Bob"));
      Map<String, Object> args = Map.of("field", "id");
      Object result = function.apply(input, args);
      assertEquals(List.of(1, 2), result);
    }

    @Test
    void apply_returnsNullForMissingField() {
      List<Map<String, Object>> input = List.of(Map.of("name", "Alice"), Map.of("name", "Bob"));
      Map<String, Object> args = Map.of("field", "age");
      Object result = function.apply(input, args);
      List<?> resultList = (List<?>) result;
      assertEquals(2, resultList.size());
      assertNull(resultList.get(0));
      assertNull(resultList.get(1));
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      assertThrows(
          IllegalArgumentException.class, () -> function.apply(List.of(Map.of("a", 1)), null));
    }

    @Test
    void apply_throwsException_whenFieldIsMissing() {
      assertThrows(
          IllegalArgumentException.class, () -> function.apply(List.of(Map.of("a", 1)), Map.of()));
    }

    @Test
    void apply_throwsException_whenFieldIsEmpty() {
      assertThrows(
          IllegalArgumentException.class,
          () -> function.apply(List.of(Map.of("a", 1)), Map.of("field", "")));
    }
  }

  @Nested
  @DisplayName("SkipNull Option Tests")
  class SkipNullTests {

    @Test
    void apply_includesNullByDefault() {
      Map<String, Object> obj1 = Map.of("name", "Alice");
      Map<String, Object> obj2 = new HashMap<>();
      obj2.put("name", null);
      Map<String, Object> obj3 = Map.of("name", "Bob");

      List<Map<String, Object>> input = List.of(obj1, obj2, obj3);
      Map<String, Object> args = Map.of("field", "name");
      Object result = function.apply(input, args);
      List<?> resultList = (List<?>) result;
      assertEquals(3, resultList.size());
      assertNull(resultList.get(1));
    }

    @Test
    void apply_skipsNullWhenEnabled() {
      Map<String, Object> obj1 = Map.of("name", "Alice");
      Map<String, Object> obj2 = new HashMap<>();
      obj2.put("name", null);
      Map<String, Object> obj3 = Map.of("name", "Bob");

      List<Map<String, Object>> input = List.of(obj1, obj2, obj3);
      Map<String, Object> args = Map.of("field", "name", "skipNull", true);
      Object result = function.apply(input, args);
      assertEquals(List.of("Alice", "Bob"), result);
    }
  }

  @Nested
  @DisplayName("Non-Map Element Handling")
  class NonMapElementTests {

    @Test
    void apply_returnsNullForNonMapElements() {
      List<Object> input = List.of("string1", "string2");
      Map<String, Object> args = Map.of("field", "name");
      Object result = function.apply(input, args);
      List<?> resultList = (List<?>) result;
      assertEquals(2, resultList.size());
      assertNull(resultList.get(0));
      assertNull(resultList.get(1));
    }

    @Test
    void apply_handlesMixedElements() {
      List<Object> input = new ArrayList<>();
      input.add(Map.of("name", "Alice"));
      input.add("not_a_map");
      input.add(Map.of("name", "Bob"));

      Map<String, Object> args = Map.of("field", "name", "skipNull", true);
      Object result = function.apply(input, args);
      assertEquals(List.of("Alice", "Bob"), result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_extractAccountNumbers() {
      List<Map<String, Object>> input =
          List.of(
              Map.of("account_no", "123", "type", "savings", "balance", 1000),
              Map.of("account_no", "456", "type", "checking", "balance", 2000),
              Map.of("account_no", "789", "type", "investment", "balance", 5000));
      Map<String, Object> args = Map.of("field", "account_no");
      Object result = function.apply(input, args);
      assertEquals(List.of("123", "456", "789"), result);
    }

    @Test
    void apply_pluckThenFilterCombination() {
      // pluck("type") -> ["savings", "checking", "savings"]
      List<Map<String, Object>> input =
          List.of(
              Map.of("account_no", "123", "type", "savings"),
              Map.of("account_no", "456", "type", "checking"),
              Map.of("account_no", "789", "type", "savings"));
      Map<String, Object> args = Map.of("field", "type");
      Object result = function.apply(input, args);
      assertEquals(List.of("savings", "checking", "savings"), result);
    }
  }
}
