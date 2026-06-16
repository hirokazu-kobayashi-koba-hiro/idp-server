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

class RemoveFunctionTest {

  private RemoveFunction function;

  @BeforeEach
  void setUp() {
    function = new RemoveFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("remove", function.name());
  }

  @Nested
  @DisplayName("Basic Remove Tests")
  class BasicRemoveTests {

    @Test
    void apply_removesElementFromList() {
      List<String> input = new ArrayList<>(List.of("a", "b", "c"));
      Map<String, Object> args = Map.of("value", "b");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "c"), result);
    }

    @Test
    void apply_removesAllOccurrences() {
      List<String> input = new ArrayList<>(List.of("a", "b", "b", "c", "b"));
      Map<String, Object> args = Map.of("value", "b");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "c"), result);
    }

    @Test
    void apply_returnsUnchangedWhenValueNotPresent() {
      List<String> input = new ArrayList<>(List.of("a", "b"));
      Map<String, Object> args = Map.of("value", "x");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b"), result);
    }

    @Test
    void apply_returnsEmptyListWhenAllRemoved() {
      List<String> input = new ArrayList<>(List.of("b", "b"));
      Map<String, Object> args = Map.of("value", "b");
      Object result = function.apply(input, args);
      assertEquals(List.of(), result);
    }

    @Test
    void apply_removesNumericValue() {
      List<Object> input = new ArrayList<>(List.of(1, 2, 3));
      Map<String, Object> args = Map.of("value", 2);
      Object result = function.apply(input, args);
      assertEquals(List.of(1, 3), result);
    }
  }

  @Nested
  @DisplayName("Null Handling")
  class NullHandlingTests {

    @Test
    void apply_returnsNullWhenInputIsNull() {
      Map<String, Object> args = Map.of("value", "x");
      Object result = function.apply(null, args);
      assertNull(result);
    }

    @Test
    void apply_returnsUnchangedWhenValueIsNull() {
      List<String> input = new ArrayList<>(List.of("a", "b"));
      Map<String, Object> args = new HashMap<>();
      args.put("value", null);
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b"), result);
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      assertThrows(IllegalArgumentException.class, () -> function.apply(List.of("a"), null));
    }

    @Test
    void apply_throwsException_whenValueArgMissing() {
      assertThrows(
          IllegalArgumentException.class, () -> function.apply(List.of("a"), Map.of("other", "x")));
    }
  }

  @Nested
  @DisplayName("Collection Type Support")
  class CollectionTypeSupportTests {

    @Test
    void apply_removesFromArray() {
      String[] input = {"a", "b", "c"};
      Map<String, Object> args = Map.of("value", "b");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "c"), result);
    }

    @Test
    void apply_removesFromSet() {
      Set<String> input = new LinkedHashSet<>(List.of("a", "b", "c"));
      Map<String, Object> args = Map.of("value", "b");
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "c"), result);
    }

    @Test
    void apply_removesFromSingleElement() {
      Map<String, Object> args = Map.of("value", "single");
      Object result = function.apply("single", args);
      assertEquals(List.of(), result);
    }

    @Test
    void apply_keepsSingleElementWhenNoMatch() {
      Map<String, Object> args = Map.of("value", "other");
      Object result = function.apply("single", args);
      assertEquals(List.of("single"), result);
    }

    @Test
    void apply_removesFromIntArray() {
      int[] input = {1, 2, 3};
      Map<String, Object> args = Map.of("value", 2);
      Object result = function.apply(input, args);
      assertEquals(List.of(1, 3), result);
    }
  }

  @Nested
  @DisplayName("Field-based Removal")
  class FieldBasedRemovalTests {

    @Test
    void apply_removesObjectByField() {
      Map<String, Object> a1 = new HashMap<>(Map.of("account_no", "123", "type", "savings"));
      Map<String, Object> a2 = new HashMap<>(Map.of("account_no", "456", "type", "checking"));
      List<Map<String, Object>> input = new ArrayList<>(List.of(a1, a2));
      Map<String, Object> args = Map.of("field", "account_no", "value", "456");
      Object result = function.apply(input, args);
      assertEquals(List.of(a1), result);
    }

    @Test
    void apply_removesAllObjectsMatchingField() {
      Map<String, Object> a1 = new HashMap<>(Map.of("id", "x", "n", 1));
      Map<String, Object> a2 = new HashMap<>(Map.of("id", "y", "n", 2));
      Map<String, Object> a3 = new HashMap<>(Map.of("id", "x", "n", 3));
      List<Map<String, Object>> input = new ArrayList<>(List.of(a1, a2, a3));
      Map<String, Object> args = Map.of("field", "id", "value", "x");
      Object result = function.apply(input, args);
      assertEquals(List.of(a2), result);
    }

    @Test
    void apply_keepsElementsMissingTheField() {
      Map<String, Object> a1 = new HashMap<>(Map.of("account_no", "123"));
      Map<String, Object> a2 = new HashMap<>(Map.of("other", "456"));
      List<Map<String, Object>> input = new ArrayList<>(List.of(a1, a2));
      Map<String, Object> args = Map.of("field", "account_no", "value", "456");
      Object result = function.apply(input, args);
      // a2 lacks account_no -> extracted null -> kept; a1.account_no != "456" -> kept
      assertEquals(List.of(a1, a2), result);
    }

    @Test
    void apply_keepsNonMapElementsWhenFieldSet() {
      List<Object> input = new ArrayList<>(List.of("scalar", Map.of("id", "2")));
      Map<String, Object> args = Map.of("field", "id", "value", "2");
      Object result = function.apply(input, args);
      // "scalar" is not a Map -> extracted null -> kept; the matching object is removed
      assertEquals(List.of("scalar"), result);
    }

    @Test
    void apply_emptyFieldFallsBackToWholeElement() {
      List<String> input = new ArrayList<>(List.of("a", "b"));
      Map<String, Object> args = Map.of("field", "", "value", "b");
      Object result = function.apply(input, args);
      assertEquals(List.of("a"), result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_deregisterServiceFromSubscriptions() {
      List<String> subscriptions = new ArrayList<>(List.of("transfers", "account", "investment"));
      // value resolved from $.request_body.service_id by dynamic args resolution
      Map<String, Object> args = Map.of("value", "account");
      Object result = function.apply(subscriptions, args);
      assertEquals(List.of("transfers", "investment"), result);
    }

    @Test
    void apply_removeAccountObjectFromExistingAccounts() {
      Map<String, Object> account1 = Map.of("account_no", "123", "type", "savings");
      Map<String, Object> account2 = Map.of("account_no", "456", "type", "checking");
      List<Map<String, Object>> input = new ArrayList<>(List.of(account1, account2));
      Map<String, Object> args = Map.of("value", account2);

      Object result = function.apply(input, args);
      assertEquals(List.of(account1), result);
    }
  }
}
