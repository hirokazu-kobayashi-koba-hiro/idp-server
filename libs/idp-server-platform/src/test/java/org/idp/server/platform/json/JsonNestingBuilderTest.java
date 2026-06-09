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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
