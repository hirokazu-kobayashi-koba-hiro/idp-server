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

package org.idp.server.platform.http;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.platform.condition.ConditionOperation;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for potential failure scenarios in ResponseSuccessCriteria JSON deserialization.
 *
 * <p>This test class investigates edge cases and validates defensive behavior.
 */
class ResponseSuccessCriteriaJsonDeserializationFailureTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  @DisplayName("rejects invalid match_mode with JsonRuntimeException - this is correct behavior")
  void testInvalidMatchModeThrowsException() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.status",
              "operation": "eq",
              "value": "success"
            }
          ],
          "match_mode": "INVALID_MODE"
        }
        """;

    // Jackson throws exception for invalid enum values - this is CORRECT behavior
    // Invalid configurations are rejected at registration time (Control Plane API)
    // This prevents invalid configurations from being stored in the database
    org.idp.server.platform.json.JsonRuntimeException exception =
        assertThrows(
            org.idp.server.platform.json.JsonRuntimeException.class,
            () -> jsonConverter.read(json, ResponseSuccessCriteria.class));

    assertTrue(
        exception.getMessage().contains("Cannot deserialize value of type"),
        "Should provide clear error message about enum deserialization failure");
    assertTrue(
        exception.getMessage().contains("ConditionMatchMode"),
        "Should indicate which enum type failed");
  }

  @Test
  @DisplayName("handles invalid operation string by converting to UNKNOWN")
  void testInvalidOperationString() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.status",
              "operation": "invalid_op",
              "value": "success"
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals("invalid_op", criteria.conditions().get(0).operation);

    // Verify ConditionOperation.from() handles invalid string
    ConditionOperation operation = criteria.conditions().get(0).operation();
    assertEquals(ConditionOperation.UNKNOWN, operation);
  }

  @Test
  @DisplayName("evaluation with null conditions list returns true (safe default)")
  void testEvaluateWithNullConditions() {
    ResponseSuccessCriteria criteria = new ResponseSuccessCriteria(null, ConditionMatchMode.ALL);
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(jsonPath);

    assertTrue(result, "Null conditions should default to true (safe behavior)");
  }

  @Test
  @DisplayName("evaluation with empty conditions returns true")
  void testEvaluateWithEmptyConditions() {
    ResponseSuccessCriteria criteria = ResponseSuccessCriteria.empty();
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"status\": \"success\"}");

    boolean result = criteria.evaluate(jsonPath);

    assertTrue(result);
  }

  @Test
  @DisplayName("handles null value in condition")
  void testConditionWithNullValue() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.error",
              "operation": "missing",
              "value": null
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertNull(criteria.conditions().get(0).value());

    // Verify evaluation works with null value
    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"status\": \"success\"}");
    boolean result = criteria.evaluate(jsonPath);
    assertTrue(result);
  }

  @Test
  @DisplayName("handles condition with missing fields by setting them to null")
  void testConditionWithMissingFields() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.status"
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals("$.status", criteria.conditions().get(0).path());
    assertNull(criteria.conditions().get(0).operation);
    assertNull(criteria.conditions().get(0).value());
  }

  @Test
  @DisplayName("handles malformed JSON path gracefully during evaluation")
  void testEvaluationWithMalformedPath() {
    ResponseSuccessCriteria criteria =
        new ResponseSuccessCriteria(
            java.util.List.of(
                new ResponseCondition("$.nonexistent.path", ConditionOperation.EQ, "value")),
            ConditionMatchMode.ALL);

    JsonPathWrapper jsonPath = new JsonPathWrapper("{\"status\": \"success\"}");

    // JsonPathWrapper.readRaw() returns null for nonexistent paths
    // ConditionOperationEvaluator should handle null actualValue
    boolean result = criteria.evaluate(jsonPath);
    assertFalse(result, "Nonexistent path should result in failed condition");
  }

  @Test
  @DisplayName("preserves operation string casing during serialization")
  void testOperationStringCasing() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.status",
              "operation": "EQ",
              "value": "success"
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    // Operation string is stored as-is
    assertEquals("EQ", criteria.conditions().get(0).operation);

    // But operation() accessor should still work (case-insensitive)
    assertEquals(ConditionOperation.EQ, criteria.conditions().get(0).operation());
  }

  @Test
  @DisplayName("toMap() returns valid structure even with null fields")
  void testToMapWithNullFields() {
    ResponseSuccessCriteria criteria = new ResponseSuccessCriteria(null, null);

    java.util.Map<String, Object> map = criteria.toMap();

    assertNotNull(map);
    // Should not throw exception, but may have missing keys
  }

  @Test
  @DisplayName("handles array value in condition")
  void testConditionWithArrayValue() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.status",
              "operation": "in",
              "value": ["approved", "success"]
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertTrue(criteria.conditions().get(0).value() instanceof java.util.List);

    @SuppressWarnings("unchecked")
    java.util.List<String> values = (java.util.List<String>) criteria.conditions().get(0).value();
    assertEquals(2, values.size());
    assertTrue(values.contains("approved"));
    assertTrue(values.contains("success"));
  }

  @Test
  @DisplayName("deserializes within full IdentityVerificationHttpRequestConfig structure")
  void testDeserializeWithinFullConfiguration() {
    String json =
        """
        {
          "url": "https://api.example.com/verify",
          "method": "POST",
          "auth_type": "oauth2",
          "response_success_criteria": {
            "conditions": [
              {
                "path": "$.status",
                "operation": "eq",
                "value": "success"
              },
              {
                "path": "$.error",
                "operation": "missing",
                "value": null
              }
            ],
            "match_mode": "ALL"
          }
        }
        """;

    // This simulates the actual usage in IdentityVerificationHttpRequestConfig
    TestConfig config = jsonConverter.read(json, TestConfig.class);

    assertNotNull(config);
    assertEquals("https://api.example.com/verify", config.url);
    assertNotNull(config.responseSuccessCriteria);
    assertEquals(2, config.responseSuccessCriteria.conditions().size());
    assertEquals(ConditionMatchMode.ALL, config.responseSuccessCriteria.matchMode());
  }

  // Test helper class that mimics IdentityVerificationHttpRequestConfig structure
  static class TestConfig {
    public String url;
    public String method;
    public String authType;
    public ResponseSuccessCriteria responseSuccessCriteria;

    public TestConfig() {}
  }
}
