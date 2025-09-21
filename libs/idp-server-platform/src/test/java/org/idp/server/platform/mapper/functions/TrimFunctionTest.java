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

public class TrimFunctionTest {

  private final TrimFunction function = new TrimFunction();

  @Test
  public void testName() {
    assertEquals("trim", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithEmptyString() {
    assertEquals("", function.apply("", null));
  }

  @Test
  public void testApplyDefaultWhitespaceTrim() {
    String result = (String) function.apply("  hello world  ", null);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithVariousWhitespace() {
    String input = " \t\n\r hello world \t\n\r ";
    String result = (String) function.apply(input, null);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyTrimBothMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "both");

    String result = (String) function.apply("  hello world  ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyTrimStartMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "start");

    String result = (String) function.apply("  hello world  ", args);
    assertEquals("hello world  ", result);
  }

  @Test
  public void testApplyTrimEndMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "end");

    String result = (String) function.apply("  hello world  ", args);
    assertEquals("  hello world", result);
  }

  @Test
  public void testApplyWithCustomChars() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", ".,;:");
    args.put("whitespace", false);

    String result = (String) function.apply("...hello world...", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithCustomCharsAndWhitespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", ".,;:");
    args.put("whitespace", true);

    String result = (String) function.apply(" ...hello world... ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithCustomCharsStartMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "start");
    args.put("chars", ".,;:");
    args.put("whitespace", false);

    String result = (String) function.apply("...hello world...", args);
    assertEquals("hello world...", result);
  }

  @Test
  public void testApplyWithCustomCharsEndMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "end");
    args.put("chars", ".,;:");
    args.put("whitespace", false);

    String result = (String) function.apply("...hello world...", args);
    assertEquals("...hello world", result);
  }

  @Test
  public void testApplyWithNormalization() {
    Map<String, Object> args = new HashMap<>();
    args.put("normalize", true);

    String result = (String) function.apply("  hello    world  ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithNormalizationNoTrim() {
    Map<String, Object> args = new HashMap<>();
    args.put("normalize", true);
    args.put("mode", "start");

    String result = (String) function.apply("  hello    world  ", args);
    assertEquals("hello world ", result);
  }

  @Test
  public void testApplyWithNormalizationInternalWhitespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("normalize", true);

    String result = (String) function.apply("hello\t\n   world\r\ntest", args);
    assertEquals("hello world test", result);
  }

  @Test
  public void testApplyOnlyCustomCharsNoWhitespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", "abc");
    args.put("whitespace", false);

    String result = (String) function.apply("aaahello worldbbb", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithUnicodeWhitespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("whitespace", true);

    // Non-breaking space (U+00A0) and em space (U+2003)
    String input = "\u00A0\u2003hello world\u00A0\u2003";
    String result = (String) function.apply(input, args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithInvalidMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "invalid");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("Invalid mode 'invalid'"));
    assertTrue(exception.getMessage().contains("both, start, end"));
  }

  @Test
  public void testApplyWithCaseSensitiveMode() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "BOTH");

    String result = (String) function.apply("  hello world  ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyNoCharactersToTrim() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", "");
    args.put("whitespace", false);

    String result = (String) function.apply("...hello world...", args);
    assertEquals("...hello world...", result);
  }

  @Test
  public void testApplyWhitespaceOnlyString() {
    String result = (String) function.apply("   \t\n   ", null);
    assertEquals("", result);
  }

  @Test
  public void testApplyCustomCharsOnlyString() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", ".,;:");
    args.put("whitespace", false);

    String result = (String) function.apply("...:::", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyMixedCustomCharsAndWhitespace() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", ".,");
    args.put("whitespace", true);

    String result = (String) function.apply(" . hello world . ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyNormalizationWithEmptyResult() {
    Map<String, Object> args = new HashMap<>();
    args.put("normalize", true);

    String result = (String) function.apply("   \t\n   ", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyNormalizationSingleWord() {
    Map<String, Object> args = new HashMap<>();
    args.put("normalize", true);

    String result = (String) function.apply("  hello  ", args);
    assertEquals("hello", result);
  }

  @Test
  public void testApplyComplexWhitespaceNormalization() {
    Map<String, Object> args = new HashMap<>();
    args.put("normalize", true);

    String input = "  hello   \t\n  world  \r\n  test  ";
    String result = (String) function.apply(input, args);
    assertEquals("hello world test", result);
  }

  @Test
  public void testApplyWithBooleanStringValues() {
    Map<String, Object> args = new HashMap<>();
    args.put("whitespace", "true");
    args.put("normalize", "false");

    String result = (String) function.apply("  hello world  ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithBooleanValues() {
    Map<String, Object> args = new HashMap<>();
    args.put("whitespace", Boolean.TRUE);
    args.put("normalize", Boolean.FALSE);

    String result = (String) function.apply("  hello world  ", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithNumericInput() {
    String result = (String) function.apply(12345, null);
    assertEquals("12345", result);
  }

  @Test
  public void testApplyStartModeWithNoLeadingChars() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "start");

    String result = (String) function.apply("hello world  ", args);
    assertEquals("hello world  ", result);
  }

  @Test
  public void testApplyEndModeWithNoTrailingChars() {
    Map<String, Object> args = new HashMap<>();
    args.put("mode", "end");

    String result = (String) function.apply("  hello world", args);
    assertEquals("  hello world", result);
  }

  @Test
  public void testApplySpecialCharactersInCustomChars() {
    Map<String, Object> args = new HashMap<>();
    args.put("chars", "[]{}()");
    args.put("whitespace", false);

    String result = (String) function.apply("[{hello world}]", args);
    assertEquals("hello world", result);
  }
}
