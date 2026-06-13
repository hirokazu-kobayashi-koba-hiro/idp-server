/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonNestingBuilderTest {

  @Test
  void flatKeyProducesFlatObject() {
    assertEquals(
        """
        {"action":"POST"}""",
        JsonNestingBuilder.buildNestedObjectJson("action", "POST"));
  }

  @Test
  void oneLevelNestedKeyProducesNestedObject() {
    assertEquals(
        """
        {"user":{"sub":"abc"}}""",
        JsonNestingBuilder.buildNestedObjectJson("user.sub", "abc"));
  }

  @Test
  void twoLevelNestedKeyProducesDoubleNestedObject() {
    assertEquals(
        """
        {"a":{"b":{"c":"d"}}}""",
        JsonNestingBuilder.buildNestedObjectJson("a.b.c", "d"));
  }

  @Test
  void valueWithSpecialCharactersIsEscaped() {
    // Double quote inside the value must be escaped by Jackson so the resulting
    // string remains valid JSON.
    assertEquals(
        """
        {"name":"he said \\"hi\\""}""",
        JsonNestingBuilder.buildNestedObjectJson("name", "he said \"hi\""));
  }

  @Test
  void emptyValueProducesEmptyString() {
    assertEquals("""
        {"key":""}""", JsonNestingBuilder.buildNestedObjectJson("key", ""));
  }

  @Test
  void nullKeyThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> JsonNestingBuilder.buildNestedObjectJson(null, "v"));
  }

  @Test
  void emptyKeyThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> JsonNestingBuilder.buildNestedObjectJson("", "v"));
  }

  @Test
  void dotOnlyKeyThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> JsonNestingBuilder.buildNestedObjectJson(".", "v"));
  }

  @Test
  void keyWithConsecutiveDotsThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonNestingBuilder.buildNestedObjectJson("a..b", "v"));
  }

  @Test
  void keyWithLeadingDotThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> JsonNestingBuilder.buildNestedObjectJson(".a", "v"));
  }

  @Test
  void keyWithTrailingDotThrows() {
    assertThrows(
        IllegalArgumentException.class, () -> JsonNestingBuilder.buildNestedObjectJson("a.", "v"));
  }

  @Test
  void typedIntegerValueProducesNumberLeaf() {
    assertEquals(
        Optional.of("{\"attempts\":3}"),
        JsonNestingBuilder.buildTypedNestedObjectJson("attempts", "3"));
  }

  @Test
  void typedDecimalValueProducesNumberLeaf() {
    assertEquals(
        Optional.of("{\"score\":1.5}"),
        JsonNestingBuilder.buildTypedNestedObjectJson("score", "1.5"));
  }

  @Test
  void typedBooleanValueProducesBooleanLeaf() {
    assertEquals(
        Optional.of("{\"flag\":true}"),
        JsonNestingBuilder.buildTypedNestedObjectJson("flag", "true"));
    assertEquals(
        Optional.of("{\"flag\":false}"),
        JsonNestingBuilder.buildTypedNestedObjectJson("flag", "false"));
  }

  @Test
  void typedNestedKeyProducesNestedNumberLeaf() {
    assertEquals(
        Optional.of("{\"user\":{\"age\":42}}"),
        JsonNestingBuilder.buildTypedNestedObjectJson("user.age", "42"));
  }

  @Test
  void nonScalarValueProducesEmpty() {
    assertTrue(JsonNestingBuilder.buildTypedNestedObjectJson("method", "POST").isEmpty());
  }

  @Test
  void leadingZeroValueStaysString() {
    // "007" must NOT be treated as numeric 7 (surprise), so the typed branch is empty
    // and only the string leaf (built separately) applies.
    assertTrue(JsonNestingBuilder.buildTypedNestedObjectJson("zip", "007").isEmpty());
  }

  @Test
  void emptyValueProducesEmptyOptional() {
    assertFalse(JsonNestingBuilder.buildTypedNestedObjectJson("k", "").isPresent());
  }
}
