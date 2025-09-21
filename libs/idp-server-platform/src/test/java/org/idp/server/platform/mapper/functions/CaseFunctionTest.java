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

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CaseFunctionTest {

  private final CaseFunction function = new CaseFunction();

  @Test
  public void testName() {
    assertEquals("case", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithEmptyString() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");

    assertEquals("", function.apply("", args));
  }

  @Test
  public void testApplyWithNullArgs() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", null));

    assertTrue(exception.getMessage().contains("'mode' argument is required"));
  }

  @Test
  public void testApplyWithMissingMode() {
    Map<String, Object> args = new HashMap<>();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("'mode' argument is required"));
  }

  @Test
  public void testApplyWithEmptyMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("'mode' argument is required"));
  }

  @Test
  public void testApplyUpperCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");

    String result = (String) function.apply("hello world", args);
    assertEquals("HELLO WORLD", result);
  }

  @Test
  public void testApplyLowerCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "lower");

    String result = (String) function.apply("HELLO WORLD", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyTitleCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("hello world", args);
    assertEquals("Hello World", result);
  }

  @Test
  public void testApplyTitleCaseWithMixedCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("hELLo WoRLd", args);
    assertEquals("Hello World", result);
  }

  @Test
  public void testApplyCamelCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("hello world", args);
    assertEquals("helloWorld", result);
  }

  @Test
  public void testApplyCamelCaseWithUnderscores() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("hello_world_test", args);
    assertEquals("helloWorldTest", result);
  }

  @Test
  public void testApplyPascalCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("hello world", args);
    assertEquals("HelloWorld", result);
  }

  @Test
  public void testApplyPascalCaseWithHyphens() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("hello-world-test", args);
    assertEquals("HelloWorldTest", result);
  }

  @Test
  public void testApplyWithCustomDelimiter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");
    args.put("delimiter", "|");

    String result = (String) function.apply("hello|world|test", args);
    assertEquals("Hello|World|Test", result);
  }

  @Test
  public void testApplyCamelCaseWithCustomDelimiter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");
    args.put("delimiter", "/");

    String result = (String) function.apply("hello/world/test", args);
    assertEquals("helloWorldTest", result);
  }

  @Test
  public void testApplyPascalCaseWithCustomDelimiter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");
    args.put("delimiter", ":");

    String result = (String) function.apply("hello:world:test", args);
    assertEquals("HelloWorldTest", result);
  }

  @Test
  public void testApplyWithLocale() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");
    args.put("locale", "tr");

    // Turkish has special case conversion rules for 'i'
    String result = (String) function.apply("istanbul", args);
    assertEquals("İSTANBUL", result);
  }

  @Test
  public void testApplyWithDefaultLocale() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");

    String result = (String) function.apply("hello", args);
    assertEquals("HELLO", result);
  }

  @Test
  public void testApplyWithInvalidMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "invalid");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("Invalid mode 'invalid'"));
    assertTrue(exception.getMessage().contains("upper, lower, title, camel, pascal"));
  }

  @Test
  public void testApplyModeIsCaseInsensitive() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "UPPER");

    String result = (String) function.apply("hello", args);
    assertEquals("HELLO", result);
  }

  @Test
  public void testApplyTitleCaseWithMultipleDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("hello world-test_case", args);
    assertEquals("Hello World-Test_Case", result);
  }

  @Test
  public void testApplyCamelCaseWithMultipleDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("hello world-test_case", args);
    assertEquals("helloWorldTestCase", result);
  }

  @Test
  public void testApplyPascalCaseWithMultipleDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("hello world-test_case", args);
    assertEquals("HelloWorldTestCase", result);
  }

  @Test
  public void testApplyTitleCaseWithDots() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("hello.world.test", args);
    assertEquals("Hello.World.Test", result);
  }

  @Test
  public void testApplyTitleCaseWithLeadingDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("   hello world", args);
    assertEquals("   Hello World", result);
  }

  @Test
  public void testApplyCamelCaseWithLeadingDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("   hello world", args);
    assertEquals("helloWorld", result);
  }

  @Test
  public void testApplyPascalCaseWithLeadingDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("   hello world", args);
    assertEquals("HelloWorld", result);
  }

  @Test
  public void testApplyTitleCaseWithTrailingDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("hello world   ", args);
    assertEquals("Hello World   ", result);
  }

  @Test
  public void testApplyCamelCaseWithTrailingDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("hello world   ", args);
    assertEquals("helloWorld", result);
  }

  @Test
  public void testApplyPascalCaseWithTrailingDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("hello world   ", args);
    assertEquals("HelloWorld", result);
  }

  @Test
  public void testApplyWithSingleCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");

    String result = (String) function.apply("a", args);
    assertEquals("A", result);
  }

  @Test
  public void testApplyTitleCaseWithSingleCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("a", args);
    assertEquals("A", result);
  }

  @Test
  public void testApplyCamelCaseWithSingleCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("a", args);
    assertEquals("a", result);
  }

  @Test
  public void testApplyPascalCaseWithSingleCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("a", args);
    assertEquals("A", result);
  }

  @Test
  public void testApplyWithNumericInput() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");

    String result = (String) function.apply(12345, args);
    assertEquals("12345", result);
  }

  @Test
  public void testApplyTitleCaseWithNumbers() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("hello123 world456", args);
    assertEquals("Hello123 World456", result);
  }

  @Test
  public void testApplyCamelCaseWithNumbers() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("hello123 world456", args);
    assertEquals("hello123World456", result);
  }

  @Test
  public void testApplyTitleCaseWithOnlyDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("   -_-   ", args);
    assertEquals("   -_-   ", result);
  }

  @Test
  public void testApplyCamelCaseWithOnlyDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "camel");

    String result = (String) function.apply("   -_-   ", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyPascalCaseWithOnlyDelimiters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "pascal");

    String result = (String) function.apply("   -_-   ", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithUnicodeCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "title");

    String result = (String) function.apply("café münü", args);
    assertEquals("Café Münü", result);
  }

  @Test
  public void testApplyUpperCaseWithUnicode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "upper");

    String result = (String) function.apply("café", args);
    assertEquals("CAFÉ", result);
  }

  @Test
  public void testApplyLowerCaseWithUnicode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "lower");

    String result = (String) function.apply("CAFÉ", args);
    assertEquals("café", result);
  }
}
