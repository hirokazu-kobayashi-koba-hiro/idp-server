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

package org.idp.server.platform.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class JsonDiffCalculatorTest {

  @Test
  void test_diff_with_added_field() {
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(Map.of("name", "Alice"));
    JsonNodeWrapper after =
        JsonNodeWrapper.fromMap(
            Map.of(
                "name", "Alice",
                "email", "alice@example.com"));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromMap(diff);

    assertEquals(1, diff.size());
    assertEquals("alice@example.com", diffJson.getValueOrEmptyAsString("email"));
  }

  @Test
  void test_diff_with_changed_field() {
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(Map.of("timeout", 3000));
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(Map.of("timeout", 5000));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromMap(diff);

    assertEquals(1, diff.size());
    assertEquals(5000, diffJson.getValueAsInt("timeout"));
  }

  @Test
  void test_diff_with_nested_object_change() {
    JsonNodeWrapper before =
        JsonNodeWrapper.fromMap(
            Map.of("provider", Map.of("endpoint", "https://old.example.com", "timeout", 3000)));
    JsonNodeWrapper after =
        JsonNodeWrapper.fromMap(
            Map.of("provider", Map.of("endpoint", "https://new.example.com", "timeout", 3000)));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromMap(diff);

    assertEquals(1, diff.size());
    assertEquals("https://new.example.com", diffJson.getValueOrEmptyAsString("provider.endpoint"));
  }

  @Test
  void test_diff_with_array_change() {
    JsonNodeWrapper before =
        JsonNodeWrapper.fromMap(Map.of("scopes", new String[] {"openid", "email"}));
    JsonNodeWrapper after =
        JsonNodeWrapper.fromMap(Map.of("scopes", new String[] {"openid", "profile", "email"}));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    assertEquals(1, diff.size());
    assertTrue(diff.containsKey("scopes"));

    // Validate array content
    Object scopesValue = diff.get("scopes");
    assertTrue(scopesValue instanceof java.util.List, "scopes should be a List");
    @SuppressWarnings("unchecked")
    java.util.List<String> scopesList = (java.util.List<String>) scopesValue;
    assertEquals(3, scopesList.size());
    assertTrue(scopesList.contains("openid"));
    assertTrue(scopesList.contains("profile"));
    assertTrue(scopesList.contains("email"));
  }

  @Test
  void test_no_diff() {
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(Map.of("name", "Alice", "age", 30));
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(Map.of("name", "Alice", "age", 30));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    assertTrue(diff.isEmpty());
  }

  @Test
  void test_diff_with_complex_nested_structure() {
    // Simulate authentication policy configuration with nested arrays and objects
    String beforeJson =
        """
        {
          "id": "policy-1",
          "policies": [
            {
              "description": "default",
              "priority": 1,
              "available_methods": ["password", "email"],
              "success_conditions": {
                "any_of": [
                  [
                    {
                      "path": "$.password-authentication.success_count",
                      "type": "integer",
                      "operation": "gte",
                      "value": 1
                    }
                  ]
                ]
              }
            }
          ]
        }
        """;

    String afterJson =
        """
        {
          "id": "policy-1",
          "policies": [
            {
              "description": "default",
              "priority": 1,
              "available_methods": ["password", "email", "sms"],
              "success_conditions": {
                "any_of": [
                  [
                    {
                      "path": "$.password-authentication.success_count",
                      "type": "integer",
                      "operation": "gte",
                      "value": 1
                    }
                  ],
                  [
                    {
                      "path": "$.sms-authentication.success_count",
                      "type": "integer",
                      "operation": "gte",
                      "value": 1
                    }
                  ]
                ]
              }
            }
          ]
        }
        """;

    JsonNodeWrapper before = JsonNodeWrapper.fromString(beforeJson);
    JsonNodeWrapper after = JsonNodeWrapper.fromString(afterJson);

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    // Should detect changes in nested arrays
    assertTrue(diff.size() > 0, "Should detect differences in complex nested structure");

    // Print diff for verification
    System.out.println("=== Complex Nested Structure Diff ===");
    diff.forEach(
        (key, value) -> {
          System.out.println(key + " = " + value);
        });

    // Verify that array changes are detected
    assertTrue(
        diff.containsKey("policies.available_methods")
            || diff.containsKey("policies.success_conditions.any_of")
            || diff.containsKey("policies"),
        "Should detect changes in policies array");
  }

  @Test
  void test_diff_with_object_array_element_change() {
    // Test with array of objects where one object property changes
    JsonNodeWrapper before =
        JsonNodeWrapper.fromMap(
            Map.of(
                "users",
                java.util.List.of(
                    Map.of("id", 1, "name", "Alice", "role", "user"),
                    Map.of("id", 2, "name", "Bob", "role", "user"))));

    JsonNodeWrapper after =
        JsonNodeWrapper.fromMap(
            Map.of(
                "users",
                java.util.List.of(
                    Map.of("id", 1, "name", "Alice", "role", "admin"),
                    Map.of("id", 2, "name", "Bob", "role", "user"))));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    assertEquals(1, diff.size());
    assertTrue(diff.containsKey("users"));

    // Validate array content
    Object usersValue = diff.get("users");
    assertTrue(usersValue instanceof java.util.List, "users should be a List");

    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> usersList =
        (java.util.List<Map<String, Object>>) usersValue;
    assertEquals(2, usersList.size());

    // Verify the changed element
    Map<String, Object> firstUser = usersList.get(0);
    assertEquals("admin", firstUser.get("role"));
  }
}
