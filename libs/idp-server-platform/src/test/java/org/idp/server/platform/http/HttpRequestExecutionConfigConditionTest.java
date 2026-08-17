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

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * Pins that {@code condition} survives storage and is echoed back (#1789).
 *
 * <p>{@code HttpRequestExecutionConfig} is embedded in many configuration types, and a field that
 * deserializes but never reaches {@code toMap()} is a failure this class has seen before: #1500's
 * {@code response_resolve_configs} was stored, invisible on GET, and inert at runtime all at once.
 * The e2e covers the runtime half; this covers the round trip.
 */
public class HttpRequestExecutionConfigConditionTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private static final String CONFIG_WITH_SIMPLE_CONDITION =
      """
      {
        "url": "https://api.example.com/notify",
        "method": "POST",
        "condition": {
          "operation": "eq",
          "path": "$.execution_http_requests[0].response_body.result",
          "value": "HIGH"
        }
      }
      """;

  private static final String CONFIG_WITH_NESTED_CONDITION =
      """
      {
        "url": "https://api.example.com/notify",
        "method": "POST",
        "condition": {
          "operation": "allOf",
          "value": [
            {"operation": "exists", "path": "$.execution_http_requests[0].response_body.score"},
            {"operation": "gte", "path": "$.execution_http_requests[0].response_body.score", "value": 80}
          ]
        }
      }
      """;

  private static final String CONFIG_WITHOUT_CONDITION =
      """
      {
        "url": "https://api.example.com/notify",
        "method": "POST"
      }
      """;

  @Test
  void readsASimpleCondition() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_SIMPLE_CONDITION, HttpRequestExecutionConfig.class);

    assertTrue(config.hasCondition());
    assertEquals("eq", config.condition().operation());
    assertEquals("$.execution_http_requests[0].response_body.result", config.condition().path());
    assertEquals("HIGH", config.condition().value());
  }

  @Test
  void echoesASimpleConditionBackOnToMap() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_SIMPLE_CONDITION, HttpRequestExecutionConfig.class);

    Map<String, Object> map = config.toMap();

    assertTrue(map.containsKey("condition"));
    @SuppressWarnings("unchecked")
    Map<String, Object> condition = (Map<String, Object>) map.get("condition");
    assertEquals("eq", condition.get("operation"));
    assertEquals("HIGH", condition.get("value"));
  }

  @Test
  void roundTripsANestedCondition() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_NESTED_CONDITION, HttpRequestExecutionConfig.class);

    String reSerialized = jsonConverter.write(config.toMap());
    HttpRequestExecutionConfig roundTripped =
        jsonConverter.read(reSerialized, HttpRequestExecutionConfig.class);

    assertTrue(roundTripped.hasCondition());
    assertEquals("allOf", roundTripped.condition().operation());

    // The nested entries have to survive too — losing them would turn a two-part gate into an
    // empty allOf, which evaluates to true and runs the request unconditionally.
    assertInstanceOf(List.class, roundTripped.condition().value());
    assertEquals(2, ((List<?>) roundTripped.condition().value()).size());
  }

  @Test
  void omitsConditionWhenNotConfigured() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITHOUT_CONDITION, HttpRequestExecutionConfig.class);

    assertFalse(config.hasCondition());
    // Absent rather than null: every configuration written before #1789 must keep serializing
    // exactly as it did.
    assertFalse(config.toMap().containsKey("condition"));
  }
}
