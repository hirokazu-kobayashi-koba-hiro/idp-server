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

class MergeFunctionTest {

  private MergeFunction function;

  @BeforeEach
  void setUp() {
    function = new MergeFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("merge", function.name());
  }

  @Nested
  @DisplayName("Basic Merge Tests")
  class BasicMergeTests {

    @Test
    void apply_mergesTwoLists() {
      List<String> input = List.of("a", "b");
      Map<String, Object> args = Map.of("source", List.of("c", "d"));
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c", "d"), result);
    }

    @Test
    void apply_mergesWhenInputIsNull() {
      Map<String, Object> args = Map.of("source", List.of("a", "b"));
      Object result = function.apply(null, args);
      assertEquals(List.of("a", "b"), result);
    }

    @Test
    void apply_mergesWithEmptySource() {
      List<String> input = List.of("a", "b");
      Map<String, Object> args = Map.of("source", List.of());
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b"), result);
    }

    @Test
    void apply_mergesWithEmptyInput() {
      List<String> input = List.of();
      Map<String, Object> args = Map.of("source", List.of("a", "b"));
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
    void apply_treatsNullSourceAsEmptyList() {
      List<String> input = List.of("a", "b");
      Object result = function.apply(input, Map.of());
      assertEquals(List.of("a", "b"), result);
    }
  }

  @Nested
  @DisplayName("Distinct Option Tests")
  class DistinctTests {

    @Test
    void apply_removeDuplicatesWithDistinct() {
      List<String> input = List.of("a", "b");
      Map<String, Object> args = Map.of("source", List.of("b", "c"), "distinct", true);
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void apply_preservesOrderWithDistinct() {
      List<String> input = List.of("c", "a");
      Map<String, Object> args = Map.of("source", List.of("b", "a"), "distinct", true);
      Object result = function.apply(input, args);
      assertEquals(List.of("c", "a", "b"), result);
    }

    @Test
    void apply_noDuplicateRemovalWithoutDistinct() {
      List<String> input = List.of("a", "b");
      Map<String, Object> args = Map.of("source", List.of("b", "c"));
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "b", "c"), result);
    }
  }

  @Nested
  @DisplayName("Key-based Deduplication Tests")
  class KeyDeduplicationTests {

    @Test
    void apply_deduplicatesByKey() {
      Map<String, Object> obj1 = Map.of("account_no", "123", "balance", 100);
      Map<String, Object> obj2 = Map.of("account_no", "456", "balance", 200);
      Map<String, Object> obj3 = Map.of("account_no", "123", "balance", 300);

      List<Map<String, Object>> input = List.of(obj1, obj2);
      Map<String, Object> args = Map.of("source", List.of(obj3), "key", "account_no");
      Object result = function.apply(input, args);

      List<?> resultList = (List<?>) result;
      assertEquals(2, resultList.size());
      // obj3 should replace obj1 since they share the same key
      @SuppressWarnings("unchecked")
      Map<String, Object> first = (Map<String, Object>) resultList.get(0);
      assertEquals(300, first.get("balance"));
      assertEquals("123", first.get("account_no"));
    }

    @Test
    void apply_preservesAllWhenKeysAreUnique() {
      Map<String, Object> obj1 = Map.of("id", "1", "name", "Alice");
      Map<String, Object> obj2 = Map.of("id", "2", "name", "Bob");

      List<Map<String, Object>> input = List.of(obj1);
      Map<String, Object> args = Map.of("source", List.of(obj2), "key", "id");
      Object result = function.apply(input, args);

      List<?> resultList = (List<?>) result;
      assertEquals(2, resultList.size());
    }
  }

  @Nested
  @DisplayName("Collection Type Support")
  class CollectionTypeSupportTests {

    @Test
    void apply_mergesArraySource() {
      List<String> input = List.of("a");
      String[] source = {"b", "c"};
      Map<String, Object> args = Map.of("source", source);
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void apply_mergesWithInputArray() {
      String[] input = {"a", "b"};
      Map<String, Object> args = Map.of("source", List.of("c"));
      Object result = function.apply(input, args);
      assertEquals(List.of("a", "b", "c"), result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_mergeAccountsFromUserResolve() {
      Map<String, Object> existing1 = Map.of("account_no", "111", "type", "savings");
      Map<String, Object> existing2 = Map.of("account_no", "222", "type", "checking");
      Map<String, Object> newAccount = Map.of("account_no", "333", "type", "investment");

      List<Map<String, Object>> input = List.of(existing1, existing2);
      Map<String, Object> args = Map.of("source", List.of(newAccount), "key", "account_no");
      Object result = function.apply(input, args);

      List<?> resultList = (List<?>) result;
      assertEquals(3, resultList.size());
    }

    @Test
    void apply_mergeAndDeduplicateAccountsByKey() {
      Map<String, Object> existing = Map.of("account_no", "111", "balance", 1000);
      Map<String, Object> updated = Map.of("account_no", "111", "balance", 2000);

      List<Map<String, Object>> input = List.of(existing);
      Map<String, Object> args = Map.of("source", List.of(updated), "key", "account_no");
      Object result = function.apply(input, args);

      List<?> resultList = (List<?>) result;
      assertEquals(1, resultList.size());
      @SuppressWarnings("unchecked")
      Map<String, Object> merged = (Map<String, Object>) resultList.get(0);
      assertEquals(2000, merged.get("balance"));
    }
  }
}
