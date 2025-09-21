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

public class RegexReplaceFunctionTest {

  private final RegexReplaceFunction function = new RegexReplaceFunction();

  @Test
  public void testName() {
    assertEquals("regex_replace", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithEmptyString() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+");
    args.put("replacement", "X");

    assertEquals("", function.apply("", args));
  }

  @Test
  public void testApplyWithNullArgs() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", null));

    assertTrue(
        exception.getMessage().contains("'pattern' and 'replacement' arguments are required"));
  }

  @Test
  public void testApplyWithMissingPattern() {
    Map<String, Object> args = new HashMap<>();
    args.put("replacement", "X");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("'pattern' argument is required"));
  }

  @Test
  public void testApplyWithMissingReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("'replacement' argument is required"));
  }

  @Test
  public void testApplyBasicRegexReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+");
    args.put("replacement", "X");

    String result = (String) function.apply("test123hello456", args);
    assertEquals("testXhelloX", result);
  }

  @Test
  public void testApplyWithCaptureGroups() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(\\d{3})-(\\d{3})-(\\d{4})");
    args.put("replacement", "($1) $2-$3");

    String result = (String) function.apply("Call me at 555-123-4567", args);
    assertEquals("Call me at (555) 123-4567", result);
  }

  @Test
  public void testApplyWithEntireMatch() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\b\\w+@\\w+\\.\\w+\\b");
    args.put("replacement", "[EMAIL:$0]");

    String result = (String) function.apply("Contact user@example.com for help", args);
    assertEquals("Contact [EMAIL:user@example.com] for help", result);
  }

  @Test
  public void testApplyReplaceFirst() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+");
    args.put("replacement", "X");
    args.put("replaceFirst", true);

    String result = (String) function.apply("test123hello456", args);
    assertEquals("testXhello456", result);
  }

  @Test
  public void testApplyWithCaseInsensitiveFlag() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "hello");
    args.put("replacement", "hi");
    args.put("flags", "i");

    String result = (String) function.apply("Hello HELLO hello", args);
    assertEquals("hi hi hi", result);
  }

  @Test
  public void testApplyWithMultilineFlag() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "^\\w+");
    args.put("replacement", "START:$0");
    args.put("flags", "m");

    String result = (String) function.apply("line1\nline2\nline3", args);
    assertEquals("START:line1\nSTART:line2\nSTART:line3", result);
  }

  @Test
  public void testApplyWithDotallFlag() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "start.*end");
    args.put("replacement", "MATCH");
    args.put("flags", "s");

    String result = (String) function.apply("start\nhello\nend", args);
    assertEquals("MATCH", result);
  }

  @Test
  public void testApplyWithMultipleFlags() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "hello.*world");
    args.put("replacement", "MATCH");
    args.put("flags", "is");

    String result = (String) function.apply("HELLO\nWORLD", args);
    assertEquals("MATCH", result);
  }

  @Test
  public void testApplyRemovePattern() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+");
    args.put("replacement", "");

    String result = (String) function.apply("abc123def456ghi", args);
    assertEquals("abcdefghi", result);
  }

  @Test
  public void testApplyWordBoundaries() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\btest\\b");
    args.put("replacement", "demo");

    String result = (String) function.apply("test testing contest test", args);
    assertEquals("demo testing contest demo", result);
  }

  @Test
  public void testApplyWithEscapedDollarSign() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+");
    args.put("replacement", "\\$price");

    String result = (String) function.apply("item costs 100 dollars", args);
    assertEquals("item costs $price dollars", result);
  }

  @Test
  public void testApplyWithBackreferences() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(\\w+)\\s+(\\w+)");
    args.put("replacement", "$2, $1");

    String result = (String) function.apply("John Doe", args);
    assertEquals("Doe, John", result);
  }

  @Test
  public void testApplyWithNestedGroups() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "((\\d{3})-(\\d{3}))-(\\d{4})");
    args.put("replacement", "$2.$3.$4");

    String result = (String) function.apply("555-123-4567", args);
    assertEquals("555.123.4567", result);
  }

  @Test
  public void testApplyWithInvalidRegex() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "[unclosed");
    args.put("replacement", "X");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("Invalid regex pattern"));
    assertTrue(exception.getMessage().contains("[unclosed"));
  }

  @Test
  public void testApplyWithInvalidReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(\\w+)");
    args.put("replacement", "$5"); // Invalid group reference

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("Error during replacement"));
  }

  @Test
  public void testApplyEmailMasking() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(\\w+)@(\\w+\\.\\w+)");
    args.put("replacement", "***@$2");

    String result = (String) function.apply("Contact john@example.com or mary@test.org", args);
    assertEquals("Contact ***@example.com or ***@test.org", result);
  }

  @Test
  public void testApplyPhoneNumberFormatting() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(\\d{3})(\\d{3})(\\d{4})");
    args.put("replacement", "$1-$2-$3");

    String result = (String) function.apply("5551234567", args);
    assertEquals("555-123-4567", result);
  }

  @Test
  public void testApplyWithUnicodeCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "café");
    args.put("replacement", "restaurant");

    String result = (String) function.apply("Visit café today", args);
    assertEquals("Visit restaurant today", result);
  }

  @Test
  public void testApplyWithEmoji() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "🍕");
    args.put("replacement", "🍔");

    String result = (String) function.apply("I love 🍕 pizza", args);
    assertEquals("I love 🍔 pizza", result);
  }

  @Test
  public void testApplyWithNumericInput() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "123");
    args.put("replacement", "456");

    String result = (String) function.apply(12345, args);
    assertEquals("45645", result);
  }

  @Test
  public void testApplyWithUnknownFlags() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "test");
    args.put("replacement", "demo");
    args.put("flags", "ixz"); // 'z' is unknown, should be ignored

    String result = (String) function.apply("TEST", args);
    assertEquals("demo", result); // Should work with 'i' flag, ignore 'z'
  }

  @Test
  public void testApplyWithCommentsFlag() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\d+  # Match digits");
    args.put("replacement", "X");
    args.put("flags", "x");

    String result = (String) function.apply("test123", args);
    assertEquals("testX", result);
  }

  @Test
  public void testApplyWithUnixLinesFlag() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "^test");
    args.put("replacement", "demo");
    args.put("flags", "d");

    String result = (String) function.apply("test line", args);
    assertEquals("demo line", result);
  }

  @Test
  public void testApplyWithEmptyPattern() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "");
    args.put("replacement", "X");

    // Empty pattern should match at every position
    String result = (String) function.apply("ab", args);
    assertEquals("XaXbX", result);
  }

  @Test
  public void testApplyPatternNotFound() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "notfound");
    args.put("replacement", "X");

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithLookahead() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\w+(?=@)");
    args.put("replacement", "USER");

    String result = (String) function.apply("email: john@example.com", args);
    assertEquals("email: USER@example.com", result);
  }

  @Test
  public void testApplyWithLookbehind() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(?<=@)\\w+");
    args.put("replacement", "DOMAIN");

    String result = (String) function.apply("email: john@example.com", args);
    assertEquals("email: john@DOMAIN.com", result);
  }

  @Test
  public void testApplyWithQuantifiers() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "o{2,}");
    args.put("replacement", "O");

    String result = (String) function.apply("foo fooo foooo", args);
    assertEquals("fO fO fO", result);
  }

  @Test
  public void testApplyWithCharacterClasses() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "[aeiou]");
    args.put("replacement", "*");
    args.put("flags", "i");

    String result = (String) function.apply("Hello World", args);
    assertEquals("H*ll* W*rld", result);
  }

  @Test
  public void testApplyComplexEmailValidation() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    args.put("replacement", "[REDACTED]");

    String result =
        (String)
            function.apply("Emails: john.doe@example.com and test.user+tag@domain.co.uk", args);
    assertEquals("Emails: [REDACTED] and [REDACTED]", result);
  }

  @Test
  public void testApplyWithOptionalGroups() {
    Map<String, Object> args = new HashMap<>();
    args.put("pattern", "(Mr\\.?|Mrs\\.?|Ms\\.?)\\s+(\\w+)");
    args.put("replacement", "$2");

    String result = (String) function.apply("Hello Mr. Smith and Mrs Jones", args);
    assertEquals("Hello Smith and Jones", result);
  }
}
