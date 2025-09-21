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

class MapFunctionTest {

  private MapFunction function;
  private FunctionRegistry functionRegistry;

  @BeforeEach
  void setUp() {
    functionRegistry = new FunctionRegistry();
    function = new MapFunction();
    function.setFunctionRegistry(functionRegistry);
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("map", function.name());
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), null));
      assertEquals("map: 'function' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenFunctionIsMissing() {
      Map<String, Object> args = Map.of("other", "value");
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), args));
      assertEquals("map: 'function' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenFunctionIsEmpty() {
      Map<String, Object> args = Map.of("function", "");
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), args));
      assertEquals("map: 'function' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenFunctionIsUnknown() {
      Map<String, Object> args = Map.of("function", "unknown_function");
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class, () -> function.apply(List.of("a", "b"), args));
      assertEquals("map: Unknown function 'unknown_function'", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Basic Map Operations")
  class BasicMapOperationsTests {

    @Test
    void apply_returnsNull_whenInputIsNull() {
      Map<String, Object> args = Map.of("function", "trim");
      Object result = function.apply(null, args);
      assertNull(result);
    }

    @Test
    void apply_appliesTrimToStringList() {
      List<String> input = List.of(" admin ", " user ", " guest ");
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_appliesFormatWithTemplate() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> functionArgs = Map.of("template", "role:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("role:admin", "role:user"), result);
    }

    @Test
    void apply_appliesCaseConversion() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> functionArgs = Map.of("mode", "upper");
      Map<String, Object> args = Map.of("function", "case", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("ADMIN", "USER"), result);
    }

    @Test
    void apply_handlesFunctionArgsNull() {
      List<String> input = List.of(" admin ", " user ");
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("admin", "user"), result);
    }
  }

  @Nested
  @DisplayName("Collection Type Support")
  class CollectionTypeSupportTests {

    @Test
    void apply_handlesArrayList() {
      List<String> input = new ArrayList<>(List.of("a", "b"));
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("a", "b"), result);
    }

    @Test
    void apply_handlesSet() {
      Set<String> input = Set.of("a", "b");
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertTrue(result.containsAll(List.of("a", "b")));
      assertEquals(2, result.size());
    }

    @Test
    void apply_handlesObjectArray() {
      String[] input = {"a", "b", "c"};
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void apply_handlesIntArray() {
      int[] input = {1, 2, 3};
      Map<String, Object> functionArgs = Map.of("template", "num:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("num:1", "num:2", "num:3"), result);
    }

    @Test
    void apply_handlesLongArray() {
      long[] input = {1L, 2L, 3L};
      Map<String, Object> functionArgs = Map.of("template", "num:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("num:1", "num:2", "num:3"), result);
    }

    @Test
    void apply_handlesDoubleArray() {
      double[] input = {1.5, 2.5, 3.5};
      Map<String, Object> functionArgs = Map.of("template", "num:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("num:1.5", "num:2.5", "num:3.5"), result);
    }

    @Test
    void apply_handlesBooleanArray() {
      boolean[] input = {true, false, true};
      Map<String, Object> functionArgs = Map.of("template", "bool:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("bool:true", "bool:false", "bool:true"), result);
    }

    @Test
    void apply_handlesSingleElement() {
      String input = "single";
      Map<String, Object> functionArgs = Map.of("template", "item:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("item:single"), result);
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    void apply_throwsException_whenFunctionApplicationFails() {
      List<String> input = List.of("valid", "invalid");
      Map<String, Object> functionArgs = Map.of("target", "test"); // Missing 'replacement' argument
      Map<String, Object> args = Map.of("function", "replace", "function_args", functionArgs);

      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> function.apply(input, args));
      assertTrue(
          exception.getMessage().contains("map: Error applying function 'replace' to element"));
    }

    @Test
    void apply_handlesNullElementsInCollection() {
      List<String> input = Arrays.asList("admin", null, "user");
      Map<String, Object> functionArgs = Map.of("template", "role:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(input, args);

      assertEquals(List.of("role:admin", "role:", "role:user"), result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_userRoleTransformation() {
      List<String> roles = List.of("ADMIN", "USER", "GUEST");
      Map<String, Object> functionArgs = Map.of("template", "ROLE_{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(roles, args);

      assertEquals(List.of("ROLE_ADMIN", "ROLE_USER", "ROLE_GUEST"), result);
    }

    @Test
    void apply_emailDomainExtraction() {
      List<String> emails = List.of("admin@example.com", "user@test.com");
      Map<String, Object> functionArgs =
          Map.of("target", "@example.com", "replacement", "@newdomain.com");
      Map<String, Object> args = Map.of("function", "replace", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(emails, args);

      assertEquals(List.of("admin@newdomain.com", "user@test.com"), result);
    }

    @Test
    void apply_dataCleanup() {
      List<String> rawData = List.of("  DATA1  ", "  DATA2  ", "  DATA3  ");
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(rawData, args);

      assertEquals(List.of("DATA1", "DATA2", "DATA3"), result);
    }

    @Test
    void apply_caseNormalization() {
      List<String> mixedCase = List.of("Admin", "USER", "guest");
      Map<String, Object> functionArgs = Map.of("mode", "lower");
      Map<String, Object> args = Map.of("function", "case", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(mixedCase, args);

      assertEquals(List.of("admin", "user", "guest"), result);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    void apply_handlesLargeCollection() {
      List<String> largeInput = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        largeInput.add("item" + i);
      }

      Map<String, Object> functionArgs = Map.of("template", "processed:{{value}}");
      Map<String, Object> args = Map.of("function", "format", "function_args", functionArgs);

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(largeInput, args);

      assertEquals(1000, result.size());
      assertEquals("processed:item0", result.get(0));
      assertEquals("processed:item999", result.get(999));
    }

    @Test
    void apply_handlesEmptyCollection() {
      List<String> emptyInput = List.of();
      Map<String, Object> args = Map.of("function", "trim");

      @SuppressWarnings("unchecked")
      List<Object> result = (List<Object>) function.apply(emptyInput, args);

      assertTrue(result.isEmpty());
    }
  }
}
