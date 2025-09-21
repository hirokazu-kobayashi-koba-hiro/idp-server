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

class SplitFunctionTest {

  private SplitFunction function;

  @BeforeEach
  void setUp() {
    function = new SplitFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("split", function.name());
  }

  @Nested
  @DisplayName("Basic Split Tests")
  class BasicSplitTests {

    @Test
    void apply_returnsNull_whenInputIsNull() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(null, args);
      assertNull(result);
    }

    @Test
    void apply_returnsEmptyList_whenInputIsEmpty() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("", args);
      assertEquals(Collections.emptyList(), result);
    }

    @Test
    void apply_splitsWithDefaultSeparator() {
      Object result = function.apply("admin,user,guest", null);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_splitsWithCustomSeparator() {
      Map<String, Object> args = Map.of("separator", "|");
      Object result = function.apply("admin|user|guest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_splitsWithMultiCharacterSeparator() {
      Map<String, Object> args = Map.of("separator", " | ");
      Object result = function.apply("admin | user | guest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_handlesSingleElement() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("admin", args);
      assertEquals(List.of("admin"), result);
    }

    @Test
    void apply_handlesNoSeparatorFound() {
      Map<String, Object> args = Map.of("separator", "|");
      Object result = function.apply("admin,user,guest", args);
      assertEquals(List.of("admin,user,guest"), result);
    }

    @Test
    void apply_handlesEmptyParts() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("admin,,user,,guest", args);
      assertEquals(List.of("admin", "", "user", "", "guest"), result);
    }
  }

  @Nested
  @DisplayName("Trim Option Tests")
  class TrimOptionTests {

    @Test
    void apply_trimsWhitespace() {
      Map<String, Object> args = Map.of("separator", ",", "trim", true);
      Object result = function.apply(" admin , user , guest ", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_doesNotTrimByDefault() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(" admin , user , guest ", args);
      assertEquals(List.of(" admin ", " user ", " guest "), result);
    }

    @Test
    void apply_trimsBooleanFalse() {
      Map<String, Object> args = Map.of("separator", ",", "trim", false);
      Object result = function.apply(" admin , user ", args);
      assertEquals(List.of(" admin ", " user "), result);
    }

    @Test
    void apply_trimsStringTrue() {
      Map<String, Object> args = Map.of("separator", ",", "trim", "true");
      Object result = function.apply(" admin , user ", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_handlesInvalidTrimValue() {
      Map<String, Object> args = Map.of("separator", ",", "trim", "invalid");
      Object result = function.apply(" admin , user ", args);
      assertEquals(List.of(" admin ", " user "), result); // defaults to false
    }
  }

  @Nested
  @DisplayName("Remove Empty Option Tests")
  class RemoveEmptyOptionTests {

    @Test
    void apply_removesEmptyParts() {
      Map<String, Object> args = Map.of("separator", ",", "removeEmpty", true);
      Object result = function.apply("admin,,user,,guest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_keepsEmptyPartsByDefault() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("admin,,user", args);
      assertEquals(List.of("admin", "", "user"), result);
    }

    @Test
    void apply_removeEmptyBooleanFalse() {
      Map<String, Object> args = Map.of("separator", ",", "removeEmpty", false);
      Object result = function.apply("admin,,user", args);
      assertEquals(List.of("admin", "", "user"), result);
    }

    @Test
    void apply_removeEmptyStringTrue() {
      Map<String, Object> args = Map.of("separator", ",", "removeEmpty", "true");
      Object result = function.apply("admin,,user", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_handlesAllEmptyParts() {
      Map<String, Object> args = Map.of("separator", ",", "removeEmpty", true);
      Object result = function.apply(",,", args);
      assertEquals(Collections.emptyList(), result);
    }
  }

  @Nested
  @DisplayName("Limit Option Tests")
  class LimitOptionTests {

    @Test
    void apply_limitsResultSize() {
      Map<String, Object> args = Map.of("separator", ",", "limit", 2);
      Object result = function.apply("admin,user,guest,moderator", args);
      assertEquals(List.of("admin", "user,guest,moderator"), result);
    }

    @Test
    void apply_noLimitByDefault() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("admin,user,guest,moderator", args);
      assertEquals(List.of("admin", "user", "guest", "moderator"), result);
    }

    @Test
    void apply_limitAsNegativeNumber() {
      Map<String, Object> args = Map.of("separator", ",", "limit", -1);
      Object result = function.apply("admin,user,guest", args);
      assertEquals(List.of("admin", "user", "guest"), result); // no limit applied
    }

    @Test
    void apply_limitAsZero() {
      Map<String, Object> args = Map.of("separator", ",", "limit", 0);
      Object result = function.apply("admin,user,guest", args);
      assertEquals(List.of("admin", "user", "guest"), result); // no limit applied
    }

    @Test
    void apply_limitAsString() {
      Map<String, Object> args = Map.of("separator", ",", "limit", "3");
      Object result = function.apply("admin,user,guest,moderator", args);
      assertEquals(List.of("admin", "user", "guest,moderator"), result);
    }

    @Test
    void apply_limitLargerThanParts() {
      Map<String, Object> args = Map.of("separator", ",", "limit", 10);
      Object result = function.apply("admin,user", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_limitAsInvalidString() {
      Map<String, Object> args = Map.of("separator", ",", "limit", "invalid");
      Object result = function.apply("admin,user,guest", args);
      assertEquals(List.of("admin", "user", "guest"), result); // defaults to -1 (no limit)
    }
  }

  @Nested
  @DisplayName("Regex Option Tests")
  class RegexOptionTests {

    @Test
    void apply_splitsWithRegexPattern() {
      Map<String, Object> args = Map.of("separator", "\\s+", "regex", true);
      Object result = function.apply("admin  user   guest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_treatsAsLiteralByDefault() {
      Map<String, Object> args = Map.of("separator", "\\s+");
      Object result = function.apply("admin\\s+user", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_regexBooleanFalse() {
      Map<String, Object> args = Map.of("separator", "\\s+", "regex", false);
      Object result = function.apply("admin\\s+user", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_regexStringTrue() {
      Map<String, Object> args = Map.of("separator", "\\s+", "regex", "true");
      Object result = function.apply("admin  user   guest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_regexWithSpecialCharacters() {
      Map<String, Object> args = Map.of("separator", "[,;]", "regex", true);
      Object result = function.apply("admin,user;guest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_regexWithLimit() {
      Map<String, Object> args = Map.of("separator", "\\s+", "regex", true, "limit", 2);
      Object result = function.apply("admin  user   guest   moderator", args);
      assertEquals(List.of("admin", "user   guest   moderator"), result);
    }

    @Test
    void apply_handlesInvalidRegex() {
      Map<String, Object> args = Map.of("separator", "[", "regex", true);
      // Invalid regex should throw an exception during pattern compilation
      assertThrows(Exception.class, () -> function.apply("admin[user", args));
    }
  }

  @Nested
  @DisplayName("Combined Options Tests")
  class CombinedOptionsTests {

    @Test
    void apply_trimAndRemoveEmpty() {
      Map<String, Object> args = Map.of("separator", ",", "trim", true, "removeEmpty", true);
      Object result = function.apply(" admin , , user , ", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_regexTrimAndLimit() {
      Map<String, Object> args =
          Map.of("separator", "\\s+", "regex", true, "trim", true, "limit", 2);
      Object result = function.apply("  admin  user   guest  ", args);
      assertEquals(List.of("", "admin  user   guest"), result);
    }

    @Test
    void apply_allOptionsEnabled() {
      Map<String, Object> args =
          Map.of(
              "separator", "[,;]",
              "regex", true,
              "trim", true,
              "removeEmpty", true,
              "limit", 3);
      Object result = function.apply(" admin , ; user ; guest , moderator ", args);
      assertEquals(List.of("admin", "user ; guest , moderator"), result);
    }

    @Test
    void apply_conflictingTrimAndRemoveEmpty() {
      Map<String, Object> args = Map.of("separator", ",", "trim", true, "removeEmpty", true);
      Object result = function.apply("   ,   ,   ", args);
      assertEquals(Collections.emptyList(), result);
    }
  }

  @Nested
  @DisplayName("Input Type Handling")
  class InputTypeHandlingTests {

    @Test
    void apply_handlesStringInput() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("admin,user", args);
      assertEquals(List.of("admin", "user"), result);
    }

    @Test
    void apply_handlesNumberInput() {
      Map<String, Object> args = Map.of("separator", ".");
      Object result = function.apply(123.456, args);
      assertEquals(List.of("123", "456"), result);
    }

    @Test
    void apply_handlesBooleanInput() {
      Map<String, Object> args = Map.of("separator", "u");
      Object result = function.apply(true, args);
      assertEquals(List.of("tr", "e"), result);
    }

    @Test
    void apply_handlesObjectInput() {
      Object input =
          new Object() {
            @Override
            public String toString() {
              return "custom,object,string";
            }
          };
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(input, args);
      assertEquals(List.of("custom", "object", "string"), result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_csvParsing() {
      Map<String, Object> args = Map.of("separator", ",", "trim", true);
      Object result = function.apply("John Doe, admin, john@example.com, active", args);
      assertEquals(List.of("John Doe", "admin", "john@example.com", "active"), result);
    }

    @Test
    void apply_permissionStringDecomposition() {
      Map<String, Object> args = Map.of("separator", ":", "removeEmpty", true);
      Object result = function.apply("read:write::admin:", args);
      assertEquals(List.of("read", "write", "admin"), result);
    }

    @Test
    void apply_tagListExtraction() {
      Map<String, Object> args =
          Map.of("separator", "\\s*,\\s*", "regex", true, "removeEmpty", true);
      Object result = function.apply("java, spring,, security, ", args);
      assertEquals(List.of("java", "spring", "security"), result);
    }

    @Test
    void apply_urlPathParsing() {
      Map<String, Object> args = Map.of("separator", "/", "removeEmpty", true);
      Object result = function.apply("/api/v1/users//admin/", args);
      assertEquals(List.of("api", "v1", "users", "admin"), result);
    }

    @Test
    void apply_logLineParsing() {
      Map<String, Object> args = Map.of("separator", "\\s+", "regex", true, "limit", 4);
      Object result =
          function.apply("2023-01-01 10:30:45 INFO Application started successfully", args);
      assertEquals(
          List.of("2023-01-01", "10:30:45", "INFO", "Application started successfully"), result);
    }

    @Test
    void apply_emailDomainExtraction() {
      Map<String, Object> args = Map.of("separator", "@", "limit", 2);
      Object result = function.apply("user@subdomain.example.com", args);
      assertEquals(List.of("user", "subdomain.example.com"), result);
    }

    @Test
    void apply_configurationValueParsing() {
      Map<String, Object> args = Map.of("separator", "=", "limit", 2, "trim", true);
      Object result = function.apply("database.url = jdbc:postgresql://localhost:5432/db", args);
      assertEquals(List.of("database.url", "jdbc:postgresql://localhost:5432/db"), result);
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @Test
    void apply_handlesLargeString() {
      StringBuilder largeInput = new StringBuilder();
      for (int i = 0; i < 1000; i++) {
        if (i > 0) largeInput.append(",");
        largeInput.append("item").append(i);
      }

      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(largeInput.toString(), args);

      @SuppressWarnings("unchecked")
      List<String> resultList = (List<String>) result;
      assertEquals(1000, resultList.size());
      assertEquals("item0", resultList.get(0));
      assertEquals("item999", resultList.get(999));
    }

    @Test
    void apply_handlesLargeStringWithRegex() {
      StringBuilder largeInput = new StringBuilder();
      for (int i = 0; i < 100; i++) {
        if (i > 0) largeInput.append("   ");
        largeInput.append("item").append(i);
      }

      Map<String, Object> args = Map.of("separator", "\\s+", "regex", true);
      Object result = function.apply(largeInput.toString(), args);

      @SuppressWarnings("unchecked")
      List<String> resultList = (List<String>) result;
      assertEquals(100, resultList.size());
      assertEquals("item0", resultList.get(0));
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    void apply_handlesOnlySeparators() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply(",,,", args);
      assertEquals(Collections.emptyList(), result);
    }

    @Test
    void apply_handlesOnlySeparatorsWithRemoveEmpty() {
      Map<String, Object> args = Map.of("separator", ",", "removeEmpty", true);
      Object result = function.apply(",,,", args);
      assertEquals(Collections.emptyList(), result);
    }

    @Test
    void apply_handlesVeryLongSeparator() {
      Map<String, Object> args = Map.of("separator", "---very-long-separator---");
      Object result = function.apply("a---very-long-separator---b", args);
      assertEquals(List.of("a", "b"), result);
    }

    @Test
    void apply_handlesSpecialCharactersInInput() {
      Map<String, Object> args = Map.of("separator", ",");
      Object result = function.apply("α,β,γ", args);
      assertEquals(List.of("α", "β", "γ"), result);
    }

    @Test
    void apply_handlesNewlinesAndTabs() {
      Map<String, Object> args = Map.of("separator", "\\s+", "regex", true, "trim", true);
      Object result = function.apply("admin\n\tuser\r\nguest", args);
      assertEquals(List.of("admin", "user", "guest"), result);
    }

    @Test
    void apply_handlesEmptyStringSeparator() {
      Map<String, Object> args = Map.of("separator", "");
      Object result = function.apply("abc", args);
      assertEquals(List.of("a", "b", "c"), result);
    }
  }
}
