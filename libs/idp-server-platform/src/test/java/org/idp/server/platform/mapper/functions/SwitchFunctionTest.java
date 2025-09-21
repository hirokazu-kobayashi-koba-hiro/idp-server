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

class SwitchFunctionTest {

  private SwitchFunction function;

  @BeforeEach
  void setUp() {
    function = new SwitchFunction();
  }

  @Test
  void name_returnsCorrectName() {
    assertEquals("switch", function.name());
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    void apply_throwsException_whenArgsIsNull() {
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", null));
      assertEquals("switch: 'cases' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenCasesIsMissing() {
      Map<String, Object> args = Map.of("default", "default value");
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("switch: 'cases' argument is required", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenCasesIsNotMap() {
      Map<String, Object> args = Map.of("cases", "invalid");
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("switch: 'cases' must be a Map", exception.getMessage());
    }

    @Test
    void apply_throwsException_whenCasesIsList() {
      Map<String, Object> args = Map.of("cases", List.of("item1", "item2"));
      IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> function.apply("input", args));
      assertEquals("switch: 'cases' must be a Map", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Basic Switch Tests")
  class BasicSwitchTests {

    @Test
    void apply_returnsMatchingCase() {
      Map<String, Object> cases =
          Map.of(
              "admin", "Administrator",
              "user", "Regular User",
              "guest", "Guest User");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply("admin", args);
      assertEquals("Administrator", result);
    }

    @Test
    void apply_returnsDefaultValue_whenNoMatch() {
      Map<String, Object> cases =
          Map.of(
              "admin", "Administrator",
              "user", "Regular User");
      Map<String, Object> args = Map.of("cases", cases, "default", "Unknown");
      Object result = function.apply("guest", args);
      assertEquals("Unknown", result);
    }

    @Test
    void apply_returnsInput_whenNoMatchAndNoDefault() {
      Map<String, Object> cases =
          Map.of(
              "admin", "Administrator",
              "user", "Regular User");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply("guest", args);
      assertEquals("guest", result);
    }

    @Test
    void apply_handlesNumericInputAndKeys() {
      Map<String, Object> cases =
          Map.of(
              "1", "One",
              "2", "Two",
              "3", "Three");
      Map<String, Object> args = Map.of("cases", cases, "default", "Unknown number");
      Object result = function.apply(1, args);
      assertEquals("One", result);
    }

    @Test
    void apply_handlesBooleanInputAndKeys() {
      Map<String, Object> cases =
          Map.of(
              "true", "Enabled",
              "false", "Disabled");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply(true, args);
      assertEquals("Enabled", result);
    }
  }

  @Nested
  @DisplayName("Null Input Tests")
  class NullInputTests {

    @Test
    void apply_returnsNullCase_whenInputIsNullAndNullCaseExists() {
      Map<String, Object> cases = new HashMap<>();
      cases.put(null, "Null value");
      cases.put("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply(null, args);
      assertEquals("Null value", result);
    }

    @Test
    void apply_returnsDefault_whenInputIsNullAndNoNullCase() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = new HashMap<>();
      args.put("cases", cases);
      args.put("default", "Default value");
      Object result = function.apply(null, args);
      assertEquals("Default value", result);
    }

    @Test
    void apply_returnsNull_whenInputIsNullAndNoNullCaseAndNoDefault() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply(null, args);
      assertNull(result);
    }
  }

  @Nested
  @DisplayName("Case Insensitive Tests")
  class CaseInsensitiveTests {

    @Test
    void apply_respectsCaseSensitivity_byDefault() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases, "default", "Unknown");
      Object result = function.apply("ADMIN", args);
      assertEquals("Unknown", result);
    }

    @Test
    void apply_ignoresCase_whenIgnoreCaseIsTrue() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases, "ignoreCase", true, "default", "Unknown");
      Object result = function.apply("ADMIN", args);
      assertEquals("Administrator", result);
    }

    @Test
    void apply_ignoresCase_whenIgnoreCaseIsStringTrue() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases, "ignoreCase", "true", "default", "Unknown");
      Object result = function.apply("Admin", args);
      assertEquals("Administrator", result);
    }

    @Test
    void apply_respectsCase_whenIgnoreCaseIsFalse() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases, "ignoreCase", false, "default", "Unknown");
      Object result = function.apply("ADMIN", args);
      assertEquals("Unknown", result);
    }

    @Test
    void apply_respectsCase_whenIgnoreCaseIsInvalidValue() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args =
          Map.of("cases", cases, "ignoreCase", "invalid", "default", "Unknown");
      Object result = function.apply("ADMIN", args);
      assertEquals("Unknown", result);
    }

    @Test
    void apply_handlesNullKeyWithCaseInsensitive() {
      Map<String, Object> cases = new HashMap<>();
      cases.put(null, "Null value");
      cases.put("admin", "Administrator");
      Map<String, Object> args = Map.of("cases", cases, "ignoreCase", true);
      Object result = function.apply(null, args);
      assertEquals("Null value", result);
    }
  }

  @Nested
  @DisplayName("Complex Value Tests")
  class ComplexValueTests {

    @Test
    void apply_handlesComplexObjectValues() {
      Map<String, Object> complexValue =
          Map.of("role", "admin", "permissions", List.of("read", "write"));
      Map<String, Object> cases = Map.of("admin", complexValue);
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply("admin", args);
      assertEquals(complexValue, result);
    }

    @Test
    void apply_handlesNullValues() {
      Map<String, Object> cases = new HashMap<>();
      cases.put("admin", null);
      cases.put("user", "Regular User");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply("admin", args);
      assertNull(result);
    }

    @Test
    void apply_handlesEmptyStringKey() {
      Map<String, Object> cases = Map.of("", "Empty string key");
      Map<String, Object> args = Map.of("cases", cases);
      Object result = function.apply("", args);
      assertEquals("Empty string key", result);
    }
  }

  @Nested
  @DisplayName("Performance and Edge Cases")
  class PerformanceAndEdgeCasesTests {

    @Test
    void apply_prefersDirectMatch_overCaseInsensitive() {
      Map<String, Object> cases =
          Map.of(
              "admin", "Direct match",
              "ADMIN", "Uppercase match");
      Map<String, Object> args = Map.of("cases", cases, "ignoreCase", true);
      Object result = function.apply("admin", args);
      assertEquals("Direct match", result);
    }

    @Test
    void apply_handlesLargeCaseMap() {
      Map<String, Object> cases = new HashMap<>();
      for (int i = 0; i < 1000; i++) {
        cases.put("key" + i, "value" + i);
      }
      Map<String, Object> args = Map.of("cases", cases, "default", "Not found");

      Object result = function.apply("key500", args);
      assertEquals("value500", result);

      Object notFoundResult = function.apply("nonexistent", args);
      assertEquals("Not found", notFoundResult);
    }

    @Test
    void apply_handlesEmptyCasesMap() {
      Map<String, Object> cases = new HashMap<>();
      Map<String, Object> args = Map.of("cases", cases, "default", "Empty map");
      Object result = function.apply("any", args);
      assertEquals("Empty map", result);
    }

    @Test
    void apply_preservesNullDefaultValue() {
      Map<String, Object> cases = Map.of("admin", "Administrator");
      Map<String, Object> args = new HashMap<>();
      args.put("cases", cases);
      args.put("default", null);
      Object result = function.apply("unknown", args);
      assertNull(result);
    }
  }

  @Nested
  @DisplayName("Real-world Use Cases")
  class RealWorldUseCasesTests {

    @Test
    void apply_userRoleMapping() {
      Map<String, Object> cases =
          Map.of(
              "ADMIN", "System Administrator",
              "USER", "Regular User",
              "GUEST", "Guest User",
              "MODERATOR", "Content Moderator");
      Map<String, Object> args = Map.of("cases", cases, "default", "Unknown Role");

      assertEquals("System Administrator", function.apply("ADMIN", args));
      assertEquals("Regular User", function.apply("USER", args));
      assertEquals("Unknown Role", function.apply("SUPERUSER", args));
    }

    @Test
    void apply_statusCodeMapping() {
      Map<String, Object> cases =
          Map.of(
              "200", "OK",
              "400", "Bad Request",
              "401", "Unauthorized",
              "403", "Forbidden",
              "404", "Not Found",
              "500", "Internal Server Error");
      Map<String, Object> args = Map.of("cases", cases, "default", "Unknown Status");

      assertEquals("OK", function.apply(200, args));
      assertEquals("Bad Request", function.apply("400", args));
      assertEquals("Unknown Status", function.apply(999, args));
    }

    @Test
    void apply_multiLanguageMapping() {
      Map<String, Object> cases =
          Map.of(
              "en", "English",
              "ja", "Japanese",
              "fr", "French",
              "de", "German");
      Map<String, Object> args = Map.of("cases", cases, "ignoreCase", true, "default", "English");

      assertEquals("Japanese", function.apply("ja", args));
      assertEquals("French", function.apply("FR", args));
      assertEquals("English", function.apply("unknown", args));
    }
  }
}
