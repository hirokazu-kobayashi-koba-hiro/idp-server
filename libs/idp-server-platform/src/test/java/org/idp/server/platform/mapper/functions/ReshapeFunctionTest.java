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

  @Nested
  @DisplayName("Per-field Function Tests")
  class PerFieldFunctionTests {

    private ReshapeFunction functionWithRegistry;

    @BeforeEach
    void setUp() {
      functionWithRegistry = new ReshapeFunction();
      FunctionRegistry registry = new FunctionRegistry();
      functionWithRegistry.setFunctionRegistry(registry);
    }

    @Test
    void apply_convertsTypeWithFunction() {
      Map<String, Object> input = Map.of("amount_str", "12345", "name", "Test");

      Map<String, Object> fields = new HashMap<>();
      fields.put("name", "$.name");
      fields.put(
          "amount",
          Map.of(
              "from",
              "$.amount_str",
              "functions",
              List.of(Map.of("name", "convert_type", "args", Map.of("type", "integer")))));

      Map<String, Object> args = Map.of("fields", fields);
      Object result = functionWithRegistry.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("Test", resultMap.get("name"));
      assertEquals(12345, resultMap.get("amount"));
    }

    @Test
    void apply_usesStaticValueInFieldSpec() {
      Map<String, Object> input = Map.of("id", "1");

      Map<String, Object> fields = new HashMap<>();
      fields.put("id", "$.id");
      fields.put("source", Map.of("static_value", "external"));

      Map<String, Object> args = Map.of("fields", fields);
      Object result = functionWithRegistry.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("1", resultMap.get("id"));
      assertEquals("external", resultMap.get("source"));
    }

    @Test
    void apply_switchesFunctionInField() {
      Map<String, Object> input = Map.of("status_code", "A", "id", "1");

      Map<String, Object> fields = new HashMap<>();
      fields.put("id", "$.id");
      fields.put(
          "status",
          Map.of(
              "from",
              "$.status_code",
              "functions",
              List.of(
                  Map.of(
                      "name",
                      "switch",
                      "args",
                      Map.of(
                          "cases",
                          Map.of("A", "active", "I", "inactive"),
                          "default",
                          "unknown")))));

      Map<String, Object> args = Map.of("fields", fields);
      Object result = functionWithRegistry.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("1", resultMap.get("id"));
      assertEquals("active", resultMap.get("status"));
    }

    @Test
    void apply_chainsMultipleFunctionsInField() {
      Map<String, Object> input = Map.of("raw_name", "  alice  ");

      Map<String, Object> fields = new HashMap<>();
      fields.put(
          "name",
          Map.of(
              "from",
              "$.raw_name",
              "functions",
              List.of(
                  Map.of("name", "trim", "args", Map.of()),
                  Map.of("name", "case", "args", Map.of("mode", "upper")))));

      Map<String, Object> args = Map.of("fields", fields);
      Object result = functionWithRegistry.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("ALICE", resultMap.get("name"));
    }

    @Test
    void apply_mixesAllFieldSpecForms() {
      Map<String, Object> input = Map.of("entity_id", "42", "raw_amount", "9999", "code", "JP");

      Map<String, Object> fields = new HashMap<>();
      // Form 1: JSONPath string
      fields.put("id", "$.entity_id");
      // Form 2: Map with from + functions
      fields.put(
          "amount",
          Map.of(
              "from",
              "$.raw_amount",
              "functions",
              List.of(Map.of("name", "convert_type", "args", Map.of("type", "integer")))));
      // Form 2: Map with static_value
      fields.put("region", Map.of("static_value", "asia"));
      // Form 3: static value (non-string)
      fields.put("priority", 1);

      Map<String, Object> args = Map.of("fields", fields);
      Object result = functionWithRegistry.apply(input, args);

      @SuppressWarnings("unchecked")
      Map<String, Object> resultMap = (Map<String, Object>) result;
      assertEquals("42", resultMap.get("id"));
      assertEquals(9999, resultMap.get("amount"));
      assertEquals("asia", resultMap.get("region"));
      assertEquals(1, resultMap.get("priority"));
    }
  }
}
