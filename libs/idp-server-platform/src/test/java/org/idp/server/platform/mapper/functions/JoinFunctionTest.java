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

class JoinFunctionTest {

  private JoinFunction function;

  @BeforeEach
  void setUp() {
    function = new JoinFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("join", function.name());
  }

  @Nested
  @DisplayName("Basic Join Tests")
  class BasicJoinTests {

    @Test
    void apply_returnsNull_whenInputIsNull() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(null, args);
      assertNull(result);
    }

    @Test
    void apply_joinsWithDefaultSeparator() {
      List<String> input = List.of("admin", "user", "guest");
      Object result = function.apply(input, null);
      assertEquals("admin,user,guest", result);
    }

    @Test
    void apply_joinsWithCustomSeparator() {
      List<String> input = List.of("admin", "user", "guest");
      Map<String, Object> args = Map.of("separator", " | ");
      Object result = function.apply(input, args);
      assertEquals("admin | user | guest", result);
    }

    @Test
    void apply_joinsWithEmptySeparator() {
      List<String> input = List.of("a", "b", "c");
      Map<String, Object> args = Map.of("separator", "");
      Object result = function.apply(input, args);
      assertEquals("abc", result);
    }

    @Test
    void apply_handlesSingleElement() {
      List<String> input = List.of("admin");
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("admin", result);
    }

    @Test
    void apply_handlesEmptyCollection() {
      List<String> input = List.of();
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("", result);
    }
  }

  @Nested
  @DisplayName("Null and Empty Handling")
  class NullAndEmptyHandlingTests {

    @Test
    void apply_includesNullByDefault() {
      List<String> input = Arrays.asList("admin", null, "user");
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("admin,null,user", result);
    }

    @Test
    void apply_skipsNullWhenRequested() {
      List<String> input = Arrays.asList("admin", null, "user", null);
      Map<String, Object> args = Map.of("separator", ",", "skipNull", true);
      Object result = function.apply(input, args);
      assertEquals("admin,user", result);
    }

    @Test
    void apply_includesEmptyByDefault() {
      List<String> input = Arrays.asList("admin", "", "user");
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("admin,,user", result);
    }

    @Test
    void apply_skipsEmptyWhenRequested() {
      List<String> input = Arrays.asList("admin", "", "user", "");
      Map<String, Object> args = Map.of("separator", ",", "skipEmpty", true);
      Object result = function.apply(input, args);
      assertEquals("admin,user", result);
    }

    @Test
    void apply_skipsBothNullAndEmpty() {
      List<String> input = Arrays.asList("admin", null, "", "user", null, "");
      Map<String, Object> args = Map.of("separator", ",", "skipNull", true, "skipEmpty", true);
      Object result = function.apply(input, args);
      assertEquals("admin,user", result);
    }

    @Test
    void apply_handlesAllNullCollection() {
      List<String> input = Arrays.asList(null, null, null);
      Map<String, Object> args = Map.of("separator", ",", "skipNull", true);
      Object result = function.apply(input, args);
      assertEquals("", result);
    }

    @Test
    void apply_handlesAllEmptyCollection() {
      List<String> input = Arrays.asList("", "", "");
      Map<String, Object> args = Map.of("separator", ",", "skipEmpty", true);
      Object result = function.apply(input, args);
      assertEquals("", result);
    }
  }

  @Nested
  @DisplayName("Prefix and Suffix Tests")
  class PrefixAndSuffixTests {

    @Test
    void apply_addsPrefix() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = Map.of("separator", ",", "prefix", "[");
      Object result = function.apply(input, args);
      assertEquals("[admin,user", result);
    }

    @Test
    void apply_addsSuffix() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = Map.of("separator", ",", "suffix", "]");
      Object result = function.apply(input, args);
      assertEquals("admin,user]", result);
    }

    @Test
    void apply_addsPrefixAndSuffix() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = Map.of("separator", ",", "prefix", "[", "suffix", "]");
      Object result = function.apply(input, args);
      assertEquals("[admin,user]", result);
    }

    @Test
    void apply_addsPrefixAndSuffixToEmptyResult() {
      List<String> input = Arrays.asList(null, null);
      Map<String, Object> args =
          Map.of("separator", ",", "prefix", "[", "suffix", "]", "skipNull", true);
      Object result = function.apply(input, args);
      assertEquals("[]", result);
    }

    @Test
    void apply_handlesNullPrefix() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = new HashMap<>();
      args.put("separator", ",");
      args.put("prefix", null);
      args.put("suffix", "]");
      Object result = function.apply(input, args);
      assertEquals("admin,user]", result);
    }

    @Test
    void apply_handlesNullSuffix() {
      List<String> input = List.of("admin", "user");
      Map<String, Object> args = new HashMap<>();
      args.put("separator", ",");
      args.put("prefix", "[");
      args.put("suffix", null);
      Object result = function.apply(input, args);
      assertEquals("[admin,user", result);
    }
  }

  @Nested
  @DisplayName("Collection Type Support")
  class CollectionTypeSupportTests {

    @Test
    void apply_handlesArrayList() {
      List<String> input = new ArrayList<>(List.of("a", "b", "c"));
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("a-b-c", result);
    }

    @Test
    void apply_handlesLinkedList() {
      List<String> input = new LinkedList<>(List.of("a", "b", "c"));
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("a-b-c", result);
    }

    @Test
    void apply_handlesSet() {
      Set<String> input = new LinkedHashSet<>(List.of("a", "b", "c"));
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("a-b-c", result);
    }

    @Test
    void apply_handlesObjectArray() {
      String[] input = {"a", "b", "c"};
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("a-b-c", result);
    }

    @Test
    void apply_handlesIntArray() {
      int[] input = {1, 2, 3};
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("1-2-3", result);
    }

    @Test
    void apply_handlesLongArray() {
      long[] input = {1L, 2L, 3L};
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("1-2-3", result);
    }

    @Test
    void apply_handlesDoubleArray() {
      double[] input = {1.1, 2.2, 3.3};
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("1.1-2.2-3.3", result);
    }

    @Test
    void apply_handlesBooleanArray() {
      boolean[] input = {true, false, true};
      Map<String, Object> args = Map.of("separator", "-");
      Object result = function.apply(input, args);
      assertEquals("true-false-true", result);
    }

    @Test
    void apply_handlesMixedTypeCollection() {
      List<Object> input = List.of("admin", 123, true, 45.6);
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("admin,123,true,45.6", result);
    }

    @Test
    void apply_handlesSingleNonCollectionElement() {
      String input = "single";
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("single", result);
    }
  }

  @Nested
  @DisplayName("Boolean Argument Handling")
  class BooleanArgumentHandlingTests {

    @Test
    void apply_handlesBooleanSkipNull() {
      List<String> input = Arrays.asList("admin", null, "user");
      Map<String, Object> args = Map.of("separator", ",", "skipNull", Boolean.TRUE);
      Object result = function.apply(input, args);
      assertEquals("admin,user", result);
    }

    @Test
    void apply_handlesStringBooleanSkipNull() {
      List<String> input = Arrays.asList("admin", null, "user");
      Map<String, Object> args = Map.of("separator", ",", "skipNull", "true");
      Object result = function.apply(input, args);
      assertEquals("admin,user", result);
    }

    @Test
    void apply_handlesStringBooleanSkipEmpty() {
      List<String> input = Arrays.asList("admin", "", "user");
      Map<String, Object> args = Map.of("separator", ",", "skipEmpty", "true");
      Object result = function.apply(input, args);
      assertEquals("admin,user", result);
    }

    @Test
    void apply_handlesFalseBooleanValues() {
      List<String> input = Arrays.asList("admin", null, "", "user");
      Map<String, Object> args = Map.of("separator", ",", "skipNull", false, "skipEmpty", "false");
      Object result = function.apply(input, args);
      assertEquals("admin,null,,user", result);
    }

    @Test
    void apply_handlesInvalidBooleanValues() {
      List<String> input = Arrays.asList("admin", null, "user");
      Map<String, Object> args = Map.of("separator", ",", "skipNull", "invalid");
      Object result = function.apply(input, args);
      assertEquals("admin,null,user", result); // defaults to false
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_csvGeneration() {
      List<String> csvRow = List.of("John", "Doe", "admin", "john@example.com");
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(csvRow, args);
      assertEquals("John,Doe,admin,john@example.com", result);
    }

    @Test
    void apply_permissionListFormatting() {
      List<String> permissions = List.of("read", "write", "admin");
      Map<String, Object> args = Map.of("separator", ", ", "prefix", "[", "suffix", "]");
      Object result = function.apply(permissions, args);
      assertEquals("[read, write, admin]", result);
    }

    @Test
    void apply_tagConcatenation() {
      List<String> tags = Arrays.asList("java", "", "spring", null, "security");
      Map<String, Object> args =
          Map.of("separator", " ", "prefix", "#", "skipNull", true, "skipEmpty", true);
      Object result = function.apply(tags, args);
      assertEquals("#java spring security", result);
    }

    @Test
    void apply_urlPathConstruction() {
      List<String> pathSegments = Arrays.asList("api", "v1", "users", "", "admin");
      Map<String, Object> args = Map.of("separator", "/", "prefix", "/", "skipEmpty", true);
      Object result = function.apply(pathSegments, args);
      assertEquals("/api/v1/users/admin", result);
    }

    @Test
    void apply_sqlInClauseGeneration() {
      List<Integer> ids = List.of(1, 2, 3, 4, 5);
      Map<String, Object> args = Map.of("separator", ",", "prefix", "(", "suffix", ")");
      Object result = function.apply(ids, args);
      assertEquals("(1,2,3,4,5)", result);
    }

    @Test
    void apply_displayNameFormatting() {
      List<String> nameParts = Arrays.asList("Dr.", null, "John", "", "Smith", "Jr.");
      Map<String, Object> args = Map.of("separator", " ", "skipNull", true, "skipEmpty", true);
      Object result = function.apply(nameParts, args);
      assertEquals("Dr. John Smith Jr.", result);
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

      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(largeInput, args);

      String resultStr = (String) result;
      assertTrue(resultStr.startsWith("item0,item1"));
      assertTrue(resultStr.endsWith("item998,item999"));
      assertEquals(1000, resultStr.split(",").length);
    }

    @Test
    void apply_handlesLargeCollectionWithSkipping() {
      List<String> largeInput = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        largeInput.add(i % 3 == 0 ? null : "item" + i);
      }

      Map<String, Object> args = Map.of("separator", ",", "skipNull", true);
      Object result = function.apply(largeInput, args);

      String resultStr = (String) result;
      assertFalse(resultStr.contains("null"));
      assertTrue(resultStr.contains("item1"));
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    void apply_handlesAllNullInputWithoutSkipping() {
      List<String> input = Arrays.asList(null, null, null);
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals("null,null,null", result);
    }

    @Test
    void apply_handlesVeryLongSeparator() {
      List<String> input = List.of("a", "b");
      Map<String, Object> args = Map.of("separator", "---very-long-separator---");
      Object result = function.apply(input, args);
      assertEquals("a---very-long-separator---b", result);
    }

    @Test
    void apply_handlesSpecialCharactersInSeparator() {
      List<String> input = List.of("a", "b", "c");
      Map<String, Object> args = Map.of("separator", "\n\t");
      Object result = function.apply(input, args);
      assertEquals("a\n\tb\n\tc", result);
    }

    @Test
    void apply_handlesUnicodeCharacters() {
      List<String> input = List.of("α", "β", "γ");
      Map<String, Object> args = Map.of("separator", "→", "prefix", "【", "suffix", "】");
      Object result = function.apply(input, args);
      assertEquals("【α→β→γ】", result);
    }
  }
}
