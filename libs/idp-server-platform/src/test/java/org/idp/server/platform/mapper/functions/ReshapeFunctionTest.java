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

class ReshapeFunctionTest {

  private ReshapeFunction function;

  @BeforeEach
  void setUp() {
    function = new ReshapeFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("reshape", function.name());
  }

  @Nested
  @DisplayName("Basic Reshape Tests")
  class BasicReshapeTests {

    @Test
    void apply_renamesFields() {
      Map<String, Object> input = Map.of("entity_id", "123", "entity_name", "Foo", "kind", "bar");
      Map<String, Object> args =
          Map.of("fields", Map.of("id", "$.entity_id", "name", "$.entity_name", "type", "$.kind"));

      Object result = function.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("123", resultMap.get("id"));
      assertEquals("Foo", resultMap.get("name"));
      assertEquals("bar", resultMap.get("type"));
    }

    @Test
    void apply_extractsNestedFields() {
      Map<String, Object> input =
          Map.of("data", Map.of("id", "456", "info", Map.of("label", "test")));
      Map<String, Object> args =
          Map.of("fields", Map.of("id", "$.data.id", "label", "$.data.info.label"));

      Object result = function.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("456", resultMap.get("id"));
      assertEquals("test", resultMap.get("label"));
    }

    @Test
    void apply_mixesJsonPathAndStaticValues() {
      Map<String, Object> input = Map.of("account_no", "789");
      Map<String, Object> fields = new HashMap<>();
      fields.put("id", "$.account_no");
      fields.put("source", "external");
      fields.put("priority", 1);
      Map<String, Object> args = Map.of("fields", fields);

      Object result = function.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("789", resultMap.get("id"));
      assertEquals("external", resultMap.get("source"));
      assertEquals(1, resultMap.get("priority"));
    }

    @Test
    void apply_returnsNullForMissingJsonPath() {
      Map<String, Object> input = Map.of("name", "Alice");
      Map<String, Object> args = Map.of("fields", Map.of("id", "$.nonexistent", "name", "$.name"));

      Object result = function.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertNull(resultMap.get("id"));
      assertEquals("Alice", resultMap.get("name"));
    }
  }

  @Nested
  @DisplayName("Null and Invalid Input Handling")
  class NullHandlingTests {

    @Test
    void apply_returnsNullWhenInputIsNull() {
      Map<String, Object> args = Map.of("fields", Map.of("id", "$.x"));
      assertNull(function.apply(null, args));
    }

    @Test
    void apply_returnsNullWhenInputIsNotMap() {
      Map<String, Object> args = Map.of("fields", Map.of("id", "$.x"));
      assertNull(function.apply("not a map", args));
    }

    @Test
    void apply_throwsWhenArgsIsNull() {
      assertThrows(IllegalArgumentException.class, () -> function.apply(Map.of("a", 1), null));
    }

    @Test
    void apply_throwsWhenFieldsIsMissing() {
      assertThrows(IllegalArgumentException.class, () -> function.apply(Map.of("a", 1), Map.of()));
    }
  }

  @Nested
  @DisplayName("Integration with MapFunction")
  class MapIntegrationTests {

    @Test
    void apply_worksWithMapFunction_transformsEachElement() {
      // Simulate what map(reshape(...)) does
      MapFunction mapFunction = new MapFunction();
      FunctionRegistry registry = new FunctionRegistry();
      mapFunction.setFunctionRegistry(registry);

      List<Map<String, Object>> input =
          List.of(
              Map.of("entity_id", "1", "entity_name", "Alice", "kind", "person"),
              Map.of("entity_id", "2", "entity_name", "Bob", "kind", "organization"));

      Map<String, Object> args =
          Map.of(
              "function",
              "reshape",
              "function_args",
              Map.of(
                  "fields",
                  Map.of("id", "$.entity_id", "name", "$.entity_name", "type", "$.kind")));

      Object result = mapFunction.apply(input, args);

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> resultList = (List<Map<String, Object>>) result;
      assertEquals(2, resultList.size());
      assertEquals("1", resultList.get(0).get("id"));
      assertEquals("Alice", resultList.get(0).get("name"));
      assertEquals("person", resultList.get(0).get("type"));
      assertEquals("2", resultList.get(1).get("id"));
      assertEquals("Bob", resultList.get(1).get("name"));
      assertEquals("organization", resultList.get(1).get("type"));
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_normalizesExternalApiResponse() {
      // Source A format: { id, name, type }
      // Source B format: { entity_id, entity_name, kind }
      // Target: { id, name, type }
      Map<String, Object> sourceBElement =
          Map.of("entity_id", "ext-001", "entity_name", "Savings Account", "kind", "savings");

      Map<String, Object> args =
          Map.of("fields", Map.of("id", "$.entity_id", "name", "$.entity_name", "type", "$.kind"));

      Object result = function.apply(sourceBElement, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> normalized = (Map<String, Object>) result;
      assertEquals("ext-001", normalized.get("id"));
      assertEquals("Savings Account", normalized.get("name"));
      assertEquals("savings", normalized.get("type"));
    }

    @Test
    void apply_extractsSubsetOfFields() {
      Map<String, Object> input =
          Map.of(
              "account_no", "123",
              "type", "savings",
              "balance", 10000,
              "currency", "JPY",
              "branch_code", "001",
              "internal_id", "xyz");

      Map<String, Object> args =
          Map.of(
              "fields",
              Map.of("account_no", "$.account_no", "type", "$.type", "balance", "$.balance"));

      Object result = function.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals(3, resultMap.size());
      assertEquals("123", resultMap.get("account_no"));
      assertEquals("savings", resultMap.get("type"));
      assertEquals(10000, resultMap.get("balance"));
    }
  }
}
