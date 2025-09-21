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

public class UuidShortFunctionTest {

  private final UuidShortFunction function = new UuidShortFunction();

  @Test
  public void testName() {
    assertEquals("uuid_short", function.name());
  }

  @Test
  public void testApplyWithDefaultParameters() {
    Map<String, Object> args = new HashMap<>();

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(8, result.length()); // default length
    assertTrue(result.matches("[abcdefghijkmnpqrstuvwxyz23456789]+"));
  }

  @Test
  public void testApplyWithCustomLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 12);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(12, result.length());
    assertTrue(result.matches("[abcdefghijkmnpqrstuvwxyz23456789]+"));
  }

  @Test
  public void testApplyWithUppercase() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 10);
    args.put("uppercase", true);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(10, result.length());
    assertTrue(result.matches("[ABCDEFGHIJKMNPQRSTUVWXYZ23456789]+"));
    assertEquals(result, result.toUpperCase());
  }

  @Test
  public void testApplyWithAmbiguousCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 10);
    args.put("exclude_ambiguous", false);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(10, result.length());
    assertTrue(result.matches("[abcdefghijklmnopqrstuvwxyz0123456789]+"));
  }

  @Test
  public void testApplyWithUppercaseAndAmbiguous() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 16);
    args.put("uppercase", true);
    args.put("exclude_ambiguous", false);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(16, result.length());
    assertTrue(result.matches("[ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789]+"));
    assertEquals(result, result.toUpperCase());
  }

  @Test
  public void testApplyWithMinLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 4);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(4, result.length());
    assertTrue(result.matches("[abcdefghijkmnpqrstuvwxyz23456789]+"));
  }

  @Test
  public void testApplyWithMaxLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 32);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(32, result.length());
    assertTrue(result.matches("[abcdefghijkmnpqrstuvwxyz23456789]+"));
  }

  @Test
  public void testApplyWithInvalidLengthTooSmall() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 3);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply(null, args));

    assertTrue(exception.getMessage().contains("Length must be between 4 and 32"));
  }

  @Test
  public void testApplyWithInvalidLengthTooLarge() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 33);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply(null, args));

    assertTrue(exception.getMessage().contains("Length must be between 4 and 32"));
  }

  @Test
  public void testApplyIgnoresInput() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 6);

    String result1 = (String) function.apply("ignored", args);
    String result2 = (String) function.apply(123, args);
    String result3 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotNull(result3);
    assertEquals(6, result1.length());
    assertEquals(6, result2.length());
    assertEquals(6, result3.length());
  }

  @Test
  public void testApplyWithNullArgs() {
    String result = (String) function.apply(null, null);

    assertNotNull(result);
    assertEquals(8, result.length()); // default length
    assertTrue(result.matches("[abcdefghijkmnpqrstuvwxyz23456789]+"));
  }

  @Test
  public void testApplyGeneratesUniqueStrings() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 16);

    String result1 = (String) function.apply(null, args);
    String result2 = (String) function.apply(null, args);
    String result3 = (String) function.apply(null, args);

    assertNotNull(result1);
    assertNotNull(result2);
    assertNotNull(result3);

    // Should be unique (statistically almost certain)
    assertNotEquals(result1, result2);
    assertNotEquals(result2, result3);
    assertNotEquals(result1, result3);
  }

  @Test
  public void testCharacterSetExcludesAmbiguous() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 32); // Maximum allowed length to test character distribution
    args.put("exclude_ambiguous", true);

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    // Should not contain ambiguous characters: 0, O, 1, l
    assertFalse(result.contains("0"));
    assertFalse(result.contains("O"));
    assertFalse(result.contains("1"));
    assertFalse(result.contains("l"));
  }

  @Test
  public void testCharacterSetIncludesAmbiguous() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", 32); // Maximum allowed length
    args.put("exclude_ambiguous", false);

    // Generate multiple strings to increase probability of finding ambiguous chars
    boolean foundAmbiguous = false;
    for (int i = 0; i < 100; i++) {
      String result = (String) function.apply(null, args);
      assertNotNull(result);
      assertEquals(32, result.length());

      // Check if this result contains ambiguous characters
      if (result.matches(".*[0ol1].*")) {
        foundAmbiguous = true;
        break;
      }
    }

    // With 100 attempts at 32-char strings, should statistically find ambiguous chars
    assertTrue(
        foundAmbiguous, "Should find at least one ambiguous character across multiple generations");
  }

  @Test
  public void testParameterParsing() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", "12"); // String instead of int
    args.put("uppercase", "true"); // String instead of boolean
    args.put("exclude_ambiguous", "false"); // String instead of boolean

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(12, result.length());
    assertEquals(result, result.toUpperCase()); // Should be uppercase
    assertTrue(result.matches("[ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789]+"));
  }

  @Test
  public void testInvalidParameterParsing() {
    Map<String, Object> args = new HashMap<>();
    args.put("length", "invalid"); // Invalid string

    String result = (String) function.apply(null, args);

    assertNotNull(result);
    assertEquals(8, result.length()); // Should fall back to default
  }
}
