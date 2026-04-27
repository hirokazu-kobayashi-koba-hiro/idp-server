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

class AppendFunctionTest {

  private AppendFunction function;

  @BeforeEach
  void setUp() {
    function = new AppendFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("append", function.name());
  }

  @Nested
  @DisplayName("Basic Append Tests")
  class BasicAppendTests {

    @Test
    void apply_appendsElementToList() {
      List<String> input = new ArrayList<>(List.of("a", "b"));
      Map<String, Object> args = Map.of("value", "c");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void apply_appendsToEmptyList() {
      List<String> input = new ArrayList<>();
      Map<String, Object> args = Map.of("value", "first");
      Object result = function.apply(input, args);
      assertEquals(List.of("first"), result);
    }

    @Test
    void apply_createsNewListWhenInputIsNull() {
      Map<String, Object> args = Map.of("value", "first");
      Object result = function.apply(null, args);
      assertEquals(List.of("first"), result);
    }

    @Test
    void apply_appendsNullValue() {
      List<String> input = new ArrayList<>(List.of("a"));
      Map<String, Object> args = new HashMap<>();
      args.put("value", null);
      Object result = function.apply(input, args);
      List<?> resultList = (List<?>) result;
      assertEquals(2, resultList.size());
      assertEquals("a", resultList.get(0));
      assertNull(resultList.get(1));
    }

    @Test
    void apply_appendsNumericValue() {
      List<Object> input = new ArrayList<>(List.of(1, 2));
      Map<String, Object> args = Map.of("value", 3);
      Object result = function.apply(input, args);
      assertEquals(List.of(1, 2, 3), result);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      assertThrows(IllegalArgumentException.class, () -> function.apply(List.of("a"), null));
    }
  }

  @Nested
  @DisplayName("Collection Type Support")
  class CollectionTypeSupportTests {

    @Test
    void apply_appendsToArray() {
      String[] input = {"a", "b"};
      Map<String, Object> args = Map.of("value", "c");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void apply_appendsToSet() {
      Set<String> input = new LinkedHashSet<>(List.of("a", "b"));
      Map<String, Object> args = Map.of("value", "c");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void apply_appendsToSingleElement() {
      Map<String, Object> args = Map.of("value", "added");
      Object result = function.apply("single", args);
      assertEquals(List.of("single", "added"), result);
    }

    @Test
    void apply_appendsToIntArray() {
      int[] input = {1, 2, 3};
      Map<String, Object> args = Map.of("value", 4);
      Object result = function.apply(input, args);
      assertEquals(List.of(1, 2, 3, 4), result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_appendAccountToExistingAccounts() {
      Map<String, Object> account1 = Map.of("account_no", "123", "type", "savings");
      Map<String, Object> account2 = Map.of("account_no", "456", "type", "checking");
      List<Map<String, Object>> input = new ArrayList<>(List.of(account1));
      Map<String, Object> args = Map.of("value", account2);

      Object result = function.apply(input, args);
      List<?> resultList = (List<?>) result;
      assertEquals(2, resultList.size());
      assertEquals(account1, resultList.get(0));
      assertEquals(account2, resultList.get(1));
    }
  }
}
