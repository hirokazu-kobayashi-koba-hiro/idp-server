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

public class SubstringFunctionTest {

  private final SubstringFunction function = new SubstringFunction();

  @Test
  public void testName() {
    assertEquals("substring", function.name());
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
  public void testApplyWithDefaultArgs() {
    String result = (String) function.apply("hello world", null);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithStartOnly() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 6);

    String result = (String) function.apply("hello world", args);
    assertEquals("world", result);
  }

  @Test
  public void testApplyWithStartAndEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("end", 5);

    String result = (String) function.apply("hello world", args);
    assertEquals("hello", result);
  }

  @Test
  public void testApplyWithStartAndLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 1);
    args.put("length", 3);

    String result = (String) function.apply("hello world", args);
    assertEquals("ell", result);
  }

  @Test
  public void testApplyWithNegativeStart() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", -5);

    String result = (String) function.apply("hello world", args);
    assertEquals("world", result);
  }

  @Test
  public void testApplyWithNegativeEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 1);
    args.put("end", -1);

    String result = (String) function.apply("hello world", args);
    assertEquals("ello worl", result);
  }

  @Test
  public void testApplyWithNegativeStartAndEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", -5);
    args.put("end", -1);

    String result = (String) function.apply("hello world", args);
    assertEquals("worl", result);
  }

  @Test
  public void testApplyWithOutOfBoundsStart() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 20);

    String result = (String) function.apply("hello world", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithOutOfBoundsEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("end", 20);

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithOutOfBoundsLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 6);
    args.put("length", 20);

    String result = (String) function.apply("hello world", args);
    assertEquals("world", result);
  }

  @Test
  public void testApplyWithNegativeStartOutOfBounds() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", -20);

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithNegativeEndOutOfBounds() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("end", -20);

    String result = (String) function.apply("hello world", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithStartGreaterThanEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 8);
    args.put("end", 5);

    String result = (String) function.apply("hello world", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithZeroLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 5);
    args.put("length", 0);

    String result = (String) function.apply("hello world", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithNegativeLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 5);
    args.put("length", -3);

    String result = (String) function.apply("hello world", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithSameStartAndEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 5);
    args.put("end", 5);

    String result = (String) function.apply("hello world", args);
    assertEquals("", result);
  }

  @Test
  public void testApplyWithSingleCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("length", 1);

    String result = (String) function.apply("hello world", args);
    assertEquals("h", result);
  }

  @Test
  public void testApplyWithLastCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", -1);

    String result = (String) function.apply("hello world", args);
    assertEquals("d", result);
  }

  @Test
  public void testApplyWithMiddleCharacter() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 5);
    args.put("length", 1);

    String result = (String) function.apply("hello world", args);
    assertEquals(" ", result);
  }

  @Test
  public void testApplyWithNumericInput() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 1);
    args.put("length", 3);

    String result = (String) function.apply(12345, args);
    assertEquals("234", result);
  }

  @Test
  public void testApplyWithStringNumberArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", "1");
    args.put("length", "3");

    String result = (String) function.apply("hello world", args);
    assertEquals("ell", result);
  }

  @Test
  public void testApplyWithInvalidStringArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", "invalid");
    args.put("length", 3);

    String result = (String) function.apply("hello world", args);
    assertEquals("hel", result); // start defaults to 0 when invalid
  }

  @Test
  public void testApplyWithNumberObjectArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", Integer.valueOf(2));
    args.put("end", Long.valueOf(7));

    String result = (String) function.apply("hello world", args);
    assertEquals("llo w", result);
  }

  @Test
  public void testApplyLengthTakesPrecedenceOverEnd() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 1);
    args.put("end", 10);
    args.put("length", 3);

    String result = (String) function.apply("hello world", args);
    assertEquals("ell", result); // length takes precedence
  }

  @Test
  public void testApplyWithUnicodeCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("length", 4);

    String result = (String) function.apply("café münü", args);
    assertEquals("café", result);
  }

  @Test
  public void testApplyWithEmoji() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 2);
    args.put("length", 4);

    String result = (String) function.apply("🍕🍔🍟", args);
    assertEquals("🍔🍟", result);
  }

  @Test
  public void testApplyEntireStringWithExplicitBounds() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("end", 11);

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyEntireStringWithLength() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("length", 11);

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithVeryLargeString() {
    String longString = "a".repeat(10000);
    Map<String, Object> args = new HashMap<>();
    args.put("start", 100);
    args.put("length", 50);

    String result = (String) function.apply(longString, args);
    assertEquals("a".repeat(50), result);
  }

  @Test
  public void testApplyWithSingleCharacterString() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("length", 1);

    String result = (String) function.apply("a", args);
    assertEquals("a", result);
  }

  @Test
  public void testApplyWithSingleCharacterStringNegativeIndex() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", -1);

    String result = (String) function.apply("a", args);
    assertEquals("a", result);
  }

  @Test
  public void testApplyExtractPrefix() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 0);
    args.put("end", 5);

    String result = (String) function.apply("prefix_suffix", args);
    assertEquals("prefi", result);
  }

  @Test
  public void testApplyExtractSuffix() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 7);

    String result = (String) function.apply("prefix_suffix", args);
    assertEquals("suffix", result);
  }

  @Test
  public void testApplyExtractMiddle() {
    Map<String, Object> args = new HashMap<>();
    args.put("start", 6);
    args.put("end", 7);

    String result = (String) function.apply("prefix_suffix", args);
    assertEquals("_", result);
  }
}
