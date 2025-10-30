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

import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests JSON deserialization safety for ResponseSuccessCriteria.
 *
 * <p>Verifies that ResponseSuccessCriteria can be safely deserialized from JSON configuration files
 * without runtime failures.
 */
class ResponseSuccessCriteriaJsonDeserializationTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  @DisplayName("deserializes valid ResponseSuccessCriteria with EQ condition")
  void testDeserializeValidCriteriaWithEqCondition() {
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
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertNotNull(criteria.conditions());
    assertEquals(1, criteria.conditions().size());
    assertEquals("$.status", criteria.conditions().get(0).path());
    assertEquals("eq", criteria.conditions().get(0).operation);
    assertEquals("success", criteria.conditions().get(0).value());
    assertEquals(ConditionMatchMode.ALL, criteria.matchMode());
  }

  @Test
  @DisplayName("deserializes valid ResponseSuccessCriteria with multiple conditions")
  void testDeserializeValidCriteriaWithMultipleConditions() {
    String json =
        """
        {
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
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals(2, criteria.conditions().size());
    assertEquals("$.status", criteria.conditions().get(0).path());
    assertEquals("$.error", criteria.conditions().get(1).path());
    assertEquals("missing", criteria.conditions().get(1).operation);
    assertNull(criteria.conditions().get(1).value());
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with ANY match mode")
  void testDeserializeWithAnyMatchMode() {
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
          "match_mode": "ANY"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals(ConditionMatchMode.ANY, criteria.matchMode());
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with boolean value")
  void testDeserializeWithBooleanValue() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.verified",
              "operation": "eq",
              "value": true
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals(1, criteria.conditions().size());
    assertEquals(true, criteria.conditions().get(0).value());
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with numeric value")
  void testDeserializeWithNumericValue() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.code",
              "operation": "eq",
              "value": 200
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals(1, criteria.conditions().size());
    assertEquals(200, criteria.conditions().get(0).value());
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with all operation types")
  void testDeserializeWithAllOperationTypes() {
    String[] operations = {
      "eq", "ne", "gt", "lt", "gte", "lte", "in", "nin", "exists", "missing", "contains", "regex"
    };

    for (String operation : operations) {
      String json =
          String.format(
              """
              {
                "conditions": [
                  {
                    "path": "$.field",
                    "operation": "%s",
                    "value": "test"
                  }
                ],
                "match_mode": "ALL"
              }
              """,
              operation);

      ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

      assertNotNull(criteria, "Should deserialize operation: " + operation);
      assertEquals(operation, criteria.conditions().get(0).operation);
    }
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with empty conditions list")
  void testDeserializeWithEmptyConditions() {
    String json =
        """
        {
          "conditions": [],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertNotNull(criteria.conditions());
    assertTrue(criteria.conditions().isEmpty());
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with missing match_mode (defaults to null)")
  void testDeserializeWithMissingMatchMode() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.status",
              "operation": "eq",
              "value": "success"
            }
          ]
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertNotNull(criteria.conditions());
    assertEquals(1, criteria.conditions().size());
    // matchMode will be null - should be handled by evaluate() method
  }

  @Test
  @DisplayName("fails to deserialize with invalid JSON syntax")
  void testDeserializeWithInvalidJsonSyntax() {
    String json = "{invalid json";

    assertThrows(
        JsonRuntimeException.class, () -> jsonConverter.read(json, ResponseSuccessCriteria.class));
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with nested path")
  void testDeserializeWithNestedPath() {
    String json =
        """
        {
          "conditions": [
            {
              "path": "$.data.verification.status",
              "operation": "eq",
              "value": "approved"
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals("$.data.verification.status", criteria.conditions().get(0).path());
  }

  @Test
  @DisplayName("deserializes ResponseSuccessCriteria with custom error_status_code")
  void testDeserializeWithCustomErrorStatusCode() {
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
          "match_mode": "ALL",
          "error_status_code": 400
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals(400, criteria.errorStatusCode());
  }

  @Test
  @DisplayName("uses default 502 when error_status_code not specified")
  void testDefaultErrorStatusCode() {
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
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

    assertNotNull(criteria);
    assertEquals(502, criteria.errorStatusCode(), "Should default to 502");
  }

  @Test
  @DisplayName("deserializes various HTTP status codes for error_status_code")
  void testDeserializeVariousErrorStatusCodes() {
    int[] statusCodes = {400, 401, 403, 404, 409, 422, 500, 502, 503};

    for (int statusCode : statusCodes) {
      String json =
          String.format(
              """
              {
                "conditions": [
                  {
                    "path": "$.error",
                    "operation": "exists",
                    "value": null
                  }
                ],
                "match_mode": "ALL",
                "error_status_code": %d
              }
              """,
              statusCode);

      ResponseSuccessCriteria criteria = jsonConverter.read(json, ResponseSuccessCriteria.class);

      assertNotNull(criteria, "Should deserialize with status code: " + statusCode);
      assertEquals(statusCode, criteria.errorStatusCode());
    }
  }

  @Test
  @DisplayName("round-trip serialization preserves data")
  void testRoundTripSerialization() {
    String originalJson =
        """
        {
          "conditions": [
            {
              "path": "$.status",
              "operation": "eq",
              "value": "success"
            }
          ],
          "match_mode": "ALL"
        }
        """;

    ResponseSuccessCriteria criteria =
        jsonConverter.read(originalJson, ResponseSuccessCriteria.class);
    String serialized = jsonConverter.write(criteria.toMap());
    ResponseSuccessCriteria deserialized =
        jsonConverter.read(serialized, ResponseSuccessCriteria.class);

    assertNotNull(deserialized);
    assertEquals(criteria.conditions().size(), deserialized.conditions().size());
    assertEquals(criteria.conditions().get(0).path(), deserialized.conditions().get(0).path());
    assertEquals(
        criteria.conditions().get(0).operation, deserialized.conditions().get(0).operation);
    assertEquals(criteria.conditions().get(0).value(), deserialized.conditions().get(0).value());
  }
}
