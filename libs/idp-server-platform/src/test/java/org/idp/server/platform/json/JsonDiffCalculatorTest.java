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
    System.out.println("=== test_diff_with_added_field ===");
    System.out.println("diff = " + diff);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromMap(diff);

    assertEquals(1, diff.size());
    // New format: {before: null, after: <value>}
    assertTrue(diff.containsKey("email"));
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> emailDiff = (java.util.Map<String, Object>) diff.get("email");
    System.out.println("emailDiff = " + emailDiff);
    assertEquals(null, emailDiff.get("before"));
    assertEquals("alice@example.com", emailDiff.get("after"));
  }

  @Test
  void test_diff_with_changed_field() {
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(Map.of("timeout", 3000));
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(Map.of("timeout", 5000));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromMap(diff);

    assertEquals(1, diff.size());
    // New format: {before: <old>, after: <new>}
    assertEquals(3000, diffJson.getValueAsJsonNode("timeout").getValueAsInt("before"));
    assertEquals(5000, diffJson.getValueAsJsonNode("timeout").getValueAsInt("after"));
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
    // New format: {before: <old>, after: <new>}
    JsonNodeWrapper endpointDiff = diffJson.getValueAsJsonNode("provider.endpoint");
    assertEquals("https://old.example.com", endpointDiff.getValueOrEmptyAsString("before"));
    assertEquals("https://new.example.com", endpointDiff.getValueOrEmptyAsString("after"));
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

    // New format: {before: <old>, after: <new>}
    Object scopesValue = diff.get("scopes");
    assertTrue(scopesValue instanceof java.util.Map, "scopes should be a Map with before/after");
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> scopesDiff = (java.util.Map<String, Object>) scopesValue;

    @SuppressWarnings("unchecked")
    java.util.List<String> beforeList = (java.util.List<String>) scopesDiff.get("before");
    assertEquals(2, beforeList.size());
    assertTrue(beforeList.contains("openid"));
    assertTrue(beforeList.contains("email"));

    @SuppressWarnings("unchecked")
    java.util.List<String> afterList = (java.util.List<String>) scopesDiff.get("after");
    assertEquals(3, afterList.size());
    assertTrue(afterList.contains("openid"));
    assertTrue(afterList.contains("profile"));
    assertTrue(afterList.contains("email"));
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

    // New format: {before: <old>, after: <new>}
    Object usersValue = diff.get("users");
    assertTrue(usersValue instanceof java.util.Map, "users should be a Map with before/after");

    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> usersDiff = (java.util.Map<String, Object>) usersValue;

    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> afterList =
        (java.util.List<Map<String, Object>>) usersDiff.get("after");
    assertEquals(2, afterList.size());

    // Verify the changed element
    Map<String, Object> firstUser = afterList.get(0);
    assertEquals("admin", firstUser.get("role"));
  }

  @Test
  void test_no_diff_when_both_null() {
    // Test that null -> null does not produce a diff
    String beforeJson =
        """
        {
          "name": "Alice",
          "attributes": null
        }
        """;

    String afterJson =
        """
        {
          "name": "Alice",
          "attributes": null
        }
        """;

    JsonNodeWrapper before = JsonNodeWrapper.fromString(beforeJson);
    JsonNodeWrapper after = JsonNodeWrapper.fromString(afterJson);

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    System.out.println("=== test_no_diff_when_both_null ===");
    System.out.println("diff = " + diff);

    assertTrue(diff.isEmpty(), "No diff should be produced when both values are null");
  }

  @Test
  void test_no_diff_when_field_missing_in_both() {
    // Test that missing field in both does not produce a diff
    JsonNodeWrapper before = JsonNodeWrapper.fromMap(Map.of("name", "Alice"));
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(Map.of("name", "Alice"));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);

    assertTrue(diff.isEmpty(), "No diff should be produced when field is missing in both");
  }

  @Test
  void test_diff_with_removed_field() {
    JsonNodeWrapper before =
        JsonNodeWrapper.fromMap(
            Map.of(
                "name", "Alice",
                "email", "alice@example.com",
                "roles", java.util.List.of("admin", "user")));
    JsonNodeWrapper after = JsonNodeWrapper.fromMap(Map.of("name", "Alice"));

    Map<String, Object> diff = JsonDiffCalculator.deepDiff(before, after);
    JsonNodeWrapper diffJson = JsonNodeWrapper.fromMap(diff);

    assertEquals(2, diff.size());

    // email removed: {before: "alice@example.com", after: null}
    assertTrue(diff.containsKey("email"));
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> emailDiff = (java.util.Map<String, Object>) diff.get("email");
    assertEquals("alice@example.com", emailDiff.get("before"));
    assertEquals(null, emailDiff.get("after"));

    // roles removed: {before: ["admin", "user"], after: null}
    assertTrue(diffJson.contains("roles"));
    @SuppressWarnings("unchecked")
    java.util.List<String> beforeRoles =
        (java.util.List<String>) ((java.util.Map<String, Object>) diff.get("roles")).get("before");
    assertEquals(2, beforeRoles.size());
    assertTrue(beforeRoles.contains("admin"));
    assertTrue(beforeRoles.contains("user"));
    assertEquals(null, ((java.util.Map<String, Object>) diff.get("roles")).get("after"));
  }
}
