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

class ConcatFunctionTest {

  private ConcatFunction function;

  @BeforeEach
  void setUp() {
    function = new ConcatFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("concat", function.name());
  }

  @Nested
  @DisplayName("Basic Concat Tests")
  class BasicConcatTests {

    @Test
    void apply_concatenatesStrings() {
      Map<String, Object> args = Map.of("values", List.of("Hello", " ", "World"));
      assertEquals("Hello World", function.apply(null, args));
    }

    @Test
    void apply_concatenatesWithSeparator() {
      Map<String, Object> args = Map.of("values", List.of("a", "b", "c"), "separator", "-");
      assertEquals("a-b-c", function.apply(null, args));
    }

    @Test
    void apply_handlesSingleValue() {
      Map<String, Object> args = Map.of("values", List.of("only"));
      assertEquals("only", function.apply(null, args));
    }

    @Test
    void apply_handlesEmptyList() {
      Map<String, Object> args = Map.of("values", List.of());
      assertEquals("", function.apply(null, args));
    }

    @Test
    void apply_handlesMixedTypes() {
      Map<String, Object> args = Map.of("values", List.of("count:", 42, " ok:", true));
      assertEquals("count:42 ok:true", function.apply(null, args));
    }
  }

  @Nested
  @DisplayName("Null and Empty Handling")
  class NullHandlingTests {

    @Test
    void apply_includesNullAsEmptyByDefault() {
      List<Object> values = new ArrayList<>();
      values.add("a");
      values.add(null);
      values.add("b");
      Map<String, Object> args = Map.of("values", values);
      assertEquals("ab", function.apply(null, args));
    }

    @Test
    void apply_skipsNullWhenEnabled() {
      List<Object> values = new ArrayList<>();
      values.add("a");
      values.add(null);
      values.add("b");
      Map<String, Object> args = Map.of("values", values, "separator", "-", "skipNull", true);
      assertEquals("a-b", function.apply(null, args));
    }

    @Test
    void apply_skipsEmptyWhenEnabled() {
      Map<String, Object> args =
          Map.of("values", List.of("a", "", "b"), "separator", "-", "skipEmpty", true);
      assertEquals("a-b", function.apply(null, args));
    }

    @Test
    void apply_throwsWhenArgsIsNull() {
      assertThrows(IllegalArgumentException.class, () -> function.apply(null, null));
    }

    @Test
    void apply_throwsWhenValuesIsMissing() {
      assertThrows(IllegalArgumentException.class, () -> function.apply(null, Map.of()));
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_concatenatesNameParts() {
      // Simulates resolved args: "$.first_name" → "Taro", "$.last_name" → "Tanaka"
      Map<String, Object> args = Map.of("values", List.of("Taro", " ", "Tanaka"));
      assertEquals("Taro Tanaka", function.apply(null, args));
    }

    @Test
    void apply_buildsAddress() {
      Map<String, Object> args =
          Map.of("values", List.of("Tokyo", "Shibuya", "1-2-3"), "separator", ", ");
      assertEquals("Tokyo, Shibuya, 1-2-3", function.apply(null, args));
    }
  }
}
