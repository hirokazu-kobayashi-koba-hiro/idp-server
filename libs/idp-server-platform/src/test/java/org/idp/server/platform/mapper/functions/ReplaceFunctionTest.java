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

public class ReplaceFunctionTest {

  private final ReplaceFunction function = new ReplaceFunction();

  @Test
  public void testName() {
    assertEquals("replace", function.name());
  }

  @Test
  public void testApplyWithNullInput() {
    assertNull(function.apply(null, null));
  }

  @Test
  public void testApplyWithEmptyString() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "demo");

    assertEquals("", function.apply("", args));
  }

  @Test
  public void testApplyWithNullArgs() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", null));

    assertTrue(
        exception.getMessage().contains("'target' and 'replacement' arguments are required"));
  }

  @Test
  public void testApplyWithMissingTarget() {
    Map<String, Object> args = new HashMap<>();
    args.put("replacement", "demo");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("'target' argument is required"));
  }

  @Test
  public void testApplyWithMissingReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> function.apply("test", args));

    assertTrue(exception.getMessage().contains("'replacement' argument is required"));
  }

  @Test
  public void testApplyBasicReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "world");
    args.put("replacement", "universe");

    String result = (String) function.apply("hello world", args);
    assertEquals("hello universe", result);
  }

  @Test
  public void testApplyReplaceAll() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "demo");

    String result = (String) function.apply("test test test", args);
    assertEquals("demo demo demo", result);
  }

  @Test
  public void testApplyReplaceFirst() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "demo");
    args.put("replaceFirst", true);

    String result = (String) function.apply("test test test", args);
    assertEquals("demo test test", result);
  }

  @Test
  public void testApplyCaseSensitive() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "Test");
    args.put("replacement", "Demo");

    String result = (String) function.apply("test Test test", args);
    assertEquals("test Demo test", result);
  }

  @Test
  public void testApplyCaseInsensitive() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "Test");
    args.put("replacement", "Demo");
    args.put("ignoreCase", true);

    String result = (String) function.apply("test Test TEST", args);
    assertEquals("Demo Demo Demo", result);
  }

  @Test
  public void testApplyCaseInsensitiveReplaceFirst() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "Test");
    args.put("replacement", "Demo");
    args.put("ignoreCase", true);
    args.put("replaceFirst", true);

    String result = (String) function.apply("test Test TEST", args);
    assertEquals("Demo Test TEST", result);
  }

  @Test
  public void testApplyWithEmptyTarget() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "");
    args.put("replacement", "demo");

    String result = (String) function.apply("test", args);
    assertEquals("test", result);
  }

  @Test
  public void testApplyWithEmptyReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "");

    String result = (String) function.apply("test hello test", args);
    assertEquals(" hello ", result);
  }

  @Test
  public void testApplyTargetNotFound() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "notfound");
    args.put("replacement", "demo");

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyTargetNotFoundCaseInsensitive() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "NOTFOUND");
    args.put("replacement", "demo");
    args.put("ignoreCase", true);

    String result = (String) function.apply("hello world", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyWithSpecialCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", ".*");
    args.put("replacement", "STAR");

    String result = (String) function.apply("file.* pattern", args);
    assertEquals("fileSTAR pattern", result);
  }

  @Test
  public void testApplyWithRegexMetacharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "$1");
    args.put("replacement", "DOLLAR");

    String result = (String) function.apply("replace $1 here", args);
    assertEquals("replace DOLLAR here", result);
  }

  @Test
  public void testApplyWithUnicodeCharacters() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "café");
    args.put("replacement", "restaurant");

    String result = (String) function.apply("visit café today", args);
    assertEquals("visit restaurant today", result);
  }

  @Test
  public void testApplyWithEmoji() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "🍕");
    args.put("replacement", "🍔");

    String result = (String) function.apply("I love 🍕 and 🍕", args);
    assertEquals("I love 🍔 and 🍔", result);
  }

  @Test
  public void testApplyWithNumericInput() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "123");
    args.put("replacement", "456");

    String result = (String) function.apply(12345, args);
    assertEquals("45645", result);
  }

  @Test
  public void testApplyWithBooleanArguments() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "demo");
    args.put("ignoreCase", Boolean.TRUE);
    args.put("replaceFirst", Boolean.FALSE);

    String result = (String) function.apply("Test TEST test", args);
    assertEquals("demo demo demo", result);
  }

  @Test
  public void testApplyWithStringBooleanArguments() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "demo");
    args.put("ignoreCase", "true");
    args.put("replaceFirst", "false");

    String result = (String) function.apply("Test TEST test", args);
    assertEquals("demo demo demo", result);
  }

  @Test
  public void testApplyOverlappingMatches() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "aa");
    args.put("replacement", "b");

    String result = (String) function.apply("aaaa", args);
    assertEquals("bb", result);
  }

  @Test
  public void testApplyOverlappingMatchesReplaceFirst() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "aa");
    args.put("replacement", "b");
    args.put("replaceFirst", true);

    String result = (String) function.apply("aaaa", args);
    assertEquals("baa", result);
  }

  @Test
  public void testApplyLongString() {
    String longInput = "test ".repeat(1000);
    Map<String, Object> args = new HashMap<>();
    args.put("target", "test");
    args.put("replacement", "demo");

    String result = (String) function.apply(longInput, args);
    assertEquals("demo ".repeat(1000), result);
  }

  @Test
  public void testApplyEntireStringReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "hello world");
    args.put("replacement", "goodbye universe");

    String result = (String) function.apply("hello world", args);
    assertEquals("goodbye universe", result);
  }

  @Test
  public void testApplyPartialMatch() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "hello");
    args.put("replacement", "hi");

    String result = (String) function.apply("hello world hello", args);
    assertEquals("hi world hi", result);
  }

  @Test
  public void testApplyWhitespaceReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", " ");
    args.put("replacement", "_");

    String result = (String) function.apply("hello world test", args);
    assertEquals("hello_world_test", result);
  }

  @Test
  public void testApplyTabReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "\t");
    args.put("replacement", "    ");

    String result = (String) function.apply("hello\tworld", args);
    assertEquals("hello    world", result);
  }

  @Test
  public void testApplyNewlineReplacement() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "\n");
    args.put("replacement", " ");

    String result = (String) function.apply("hello\nworld", args);
    assertEquals("hello world", result);
  }

  @Test
  public void testApplyCaseInsensitiveMixedCase() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "tEsT");
    args.put("replacement", "DEMO");
    args.put("ignoreCase", true);

    String result = (String) function.apply("Test TEST test TeSt", args);
    assertEquals("DEMO DEMO DEMO DEMO", result);
  }

  @Test
  public void testApplyMultipleOccurrencesInWord() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "ll");
    args.put("replacement", "rr");

    String result = (String) function.apply("hello", args);
    assertEquals("herro", result);
  }

  @Test
  public void testApplyReplacementLongerThanTarget() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "a");
    args.put("replacement", "replacement");

    String result = (String) function.apply("cat", args);
    assertEquals("creplacementt", result);
  }

  @Test
  public void testApplyReplacementShorterThanTarget() {
    Map<String, Object> args = new HashMap<>();
    args.put("target", "replacement");
    args.put("replacement", "a");

    String result = (String) function.apply("test replacement here", args);
    assertEquals("test a here", result);
  }
}
