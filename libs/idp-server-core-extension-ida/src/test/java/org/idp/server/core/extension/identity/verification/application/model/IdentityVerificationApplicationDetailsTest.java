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

package org.idp.server.core.extension.identity.verification.application.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@code deep_merge} application_details policy (#1637): {@code Map} values
 * merge recursively so sibling subkeys under a shared parent are preserved, while scalars and
 * arrays overwrite.
 */
class IdentityVerificationApplicationDetailsTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deepMerge(
      Map<String, Object> target, Map<String, Object> source) {
    Map<String, Object> copy = new HashMap<>(target);
    IdentityVerificationApplicationDetails.deepMerge(copy, source);
    return copy;
  }

  @Test
  void preservesSiblingSubkeysUnderSharedParent() {
    // The core #1637 case: process B writing progress.b must not drop process A's progress.a.
    Map<String, Object> target = new HashMap<>(Map.of("progress", new HashMap<>(Map.of("a", 1))));
    Map<String, Object> source = Map.of("progress", Map.of("b", 2));

    Map<String, Object> merged = deepMerge(target, source);

    @SuppressWarnings("unchecked")
    Map<String, Object> progress = (Map<String, Object>) merged.get("progress");
    assertEquals(1, progress.get("a"));
    assertEquals(2, progress.get("b"));
  }

  @Test
  void overwritesScalarValues() {
    Map<String, Object> merged = deepMerge(new HashMap<>(Map.of("x", 1)), Map.of("x", 2));
    assertEquals(2, merged.get("x"));
  }

  @Test
  void mergesRecursivelyAtDeeperLevels() {
    Map<String, Object> target = new HashMap<>(Map.of("a", Map.of("b", Map.of("c", 1))));
    Map<String, Object> source = Map.of("a", Map.of("b", Map.of("d", 2)));

    Map<String, Object> merged = deepMerge(target, source);

    @SuppressWarnings("unchecked")
    Map<String, Object> b = (Map<String, Object>) ((Map<String, Object>) merged.get("a")).get("b");
    assertEquals(1, b.get("c"));
    assertEquals(2, b.get("d"));
  }

  @Test
  void overwritesArraysRatherThanMerging() {
    Map<String, Object> merged =
        deepMerge(new HashMap<>(Map.of("a", List.of(1, 2))), Map.of("a", List.of(3)));
    assertEquals(List.of(3), merged.get("a"));
  }

  @Test
  void overwritesWhenTypeChangesBetweenMapAndScalar() {
    // existing Map, incoming scalar -> overwrite (no merge across incompatible types)
    Map<String, Object> merged =
        deepMerge(new HashMap<>(Map.of("a", new HashMap<>(Map.of("b", 1)))), Map.of("a", "scalar"));
    assertEquals("scalar", merged.get("a"));
  }

  @Test
  void addsNewTopLevelKeys() {
    Map<String, Object> merged = deepMerge(new HashMap<>(Map.of("a", 1)), Map.of("b", 2));
    assertEquals(1, merged.get("a"));
    assertEquals(2, merged.get("b"));
  }

  @Test
  void skipsNullSourceValuesPreservingExisting() {
    // An unmatched `from` JSONPath maps to null; deep_merge must not clobber existing data with it.
    Map<String, Object> target = new HashMap<>(Map.of("progress", new HashMap<>(Map.of("a", 1))));
    Map<String, Object> source = new HashMap<>();
    source.put("progress", null);

    Map<String, Object> merged = deepMerge(target, source);

    @SuppressWarnings("unchecked")
    Map<String, Object> progress = (Map<String, Object>) merged.get("progress");
    assertEquals(1, progress.get("a"));
  }

  @Test
  void skipsNullSubkeyValuesPreservingExistingSibling() {
    Map<String, Object> target =
        new HashMap<>(Map.of("progress", new HashMap<>(Map.of("opening", "approved"))));
    Map<String, Object> investment = new HashMap<>();
    investment.put("investment", null);
    Map<String, Object> source = Map.of("progress", investment);

    Map<String, Object> merged = deepMerge(target, source);

    @SuppressWarnings("unchecked")
    Map<String, Object> progress = (Map<String, Object>) merged.get("progress");
    assertEquals("approved", progress.get("opening"));
    assertFalse(progress.containsKey("investment"));
  }
}
