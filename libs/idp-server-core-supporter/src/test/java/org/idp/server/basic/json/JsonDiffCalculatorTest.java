/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class JsonDiffCalculatorTest {

  @Test
  void test_diff_with_added_field() {
    JsonNodeWrapper before = JsonNodeWrapper.fromObject(Map.of("name", "Alice"));
    JsonNodeWrapper after =
        JsonNodeWrapper.fromObject(
            Map.of(
                "name", "Alice",
                "email", "alice@example.com"));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromObject(diff);

    assertEquals(1, diff.size());
    assertEquals("alice@example.com", diffJson.getValueOrEmptyAsString("email"));
  }

  @Test
  void test_diff_with_changed_field() {
    JsonNodeWrapper before = JsonNodeWrapper.fromObject(Map.of("timeout", 3000));
    JsonNodeWrapper after = JsonNodeWrapper.fromObject(Map.of("timeout", 5000));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromObject(diff);

    assertEquals(1, diff.size());
    assertEquals(5000, diffJson.getValueAsInt("timeout"));
  }

  @Test
  void test_diff_with_nested_object_change() {
    JsonNodeWrapper before =
        JsonNodeWrapper.fromObject(
            Map.of("provider", Map.of("endpoint", "https://old.example.com", "timeout", 3000)));
    JsonNodeWrapper after =
        JsonNodeWrapper.fromObject(
            Map.of("provider", Map.of("endpoint", "https://new.example.com", "timeout", 3000)));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromObject(diff);

    assertEquals(1, diff.size());
    assertEquals("https://new.example.com", diffJson.getValueOrEmptyAsString("provider.endpoint"));
  }

  @Test
  void test_diff_with_array_change() {
    JsonNodeWrapper before =
        JsonNodeWrapper.fromObject(Map.of("scopes", new String[] {"openid", "email"}));
    JsonNodeWrapper after =
        JsonNodeWrapper.fromObject(Map.of("scopes", new String[] {"openid", "profile", "email"}));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    assertEquals(1, diff.size());
    assertTrue(diff.containsKey("scopes"));
  }

  @Test
  void test_no_diff() {
    JsonNodeWrapper before = JsonNodeWrapper.fromObject(Map.of("name", "Alice", "age", 30));
    JsonNodeWrapper after = JsonNodeWrapper.fromObject(Map.of("name", "Alice", "age", 30));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    assertTrue(diff.isEmpty());
  }
}
