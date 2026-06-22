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
 * Verifies that {@link HttpRequestExecutionConfig} accepts {@code response_resolve_configs} in the
 * same array form as {@code IdentityVerificationHttpRequestConfig} (issue #1500).
 */
public class HttpRequestExecutionConfigResponseResolveTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private static final String CONFIG_WITH_ARRAY_FORM =
      """
      {
        "url": "https://api.example.com/introspect",
        "method": "POST",
        "auth_type": "none",
        "response_resolve_configs": [
          {
            "conditions": [
              {"path": "$.response_body.active", "operation": "eq", "value": false}
            ],
            "match_mode": "ALL",
            "mapped_status_code": 401
          }
        ]
      }
      """;

  // Legacy form persisted before #1500 (field was typed as the HttpResponseResolveConfigs wrapper).
  private static final String CONFIG_WITH_LEGACY_WRAPPER_FORM =
      """
      {
        "url": "https://api.example.com/introspect",
        "method": "POST",
        "auth_type": "none",
        "response_resolve_configs": {
          "configs": [
            {
              "conditions": [
                {"path": "$.response_body.active", "operation": "eq", "value": false}
              ],
              "match_mode": "ALL",
              "mapped_status_code": 401
            }
          ]
        }
      }
      """;

  @Test
  void deserializesResponseResolveConfigsFromArrayForm() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_ARRAY_FORM, HttpRequestExecutionConfig.class);

    assertTrue(config.hasResponseConfigs());

    HttpResponseResolveConfigs resolveConfigs = config.responseResolveConfigs();
    assertFalse(resolveConfigs.isEmpty());
    assertEquals(1, resolveConfigs.configs().size());

    HttpResponseResolveConfig resolveConfig = resolveConfigs.configs().get(0);
    assertEquals(401, resolveConfig.mappedStatusCode());
    assertEquals(1, resolveConfig.conditions().size());
    assertEquals("$.response_body.active", resolveConfig.conditions().get(0).path());
  }

  @Test
  void deserializesResponseResolveConfigsFromLegacyWrapperForm() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_LEGACY_WRAPPER_FORM, HttpRequestExecutionConfig.class);

    assertTrue(config.hasResponseConfigs());

    HttpResponseResolveConfigs resolveConfigs = config.responseResolveConfigs();
    assertEquals(1, resolveConfigs.configs().size());
    assertEquals(401, resolveConfigs.configs().get(0).mappedStatusCode());

    // Legacy wrapper input must normalize to the array form on output.
    Object emitted = config.toMap().get("response_resolve_configs");
    assertInstanceOf(List.class, emitted);
    assertEquals(1, ((List<?>) emitted).size());
  }

  @Test
  void serializesResponseResolveConfigsAsArrayViaJsonWrite() {
    // jsonConverter.write(config) is the persistence path (payload stored in DB). It must emit the
    // canonical array form, not the legacy {"configs": [...]} wrapper.
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_ARRAY_FORM, HttpRequestExecutionConfig.class);

    String json = jsonConverter.write(config);

    assertTrue(
        json.contains("\"response_resolve_configs\":[{"),
        "expected array form in serialized output but was: " + json);
    assertFalse(
        json.contains("\"response_resolve_configs\":{"),
        "serialized output must not contain the legacy wrapper form: " + json);
  }

  @Test
  void normalizesLegacyWrapperToArrayViaJsonWrite() {
    // Legacy wrapper input read from storage must be re-serialized as the array form (lazy
    // migration on re-save).
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_LEGACY_WRAPPER_FORM, HttpRequestExecutionConfig.class);

    String json = jsonConverter.write(config);

    assertTrue(
        json.contains("\"response_resolve_configs\":[{"),
        "expected array form after re-serialization but was: " + json);
    assertFalse(json.contains("\"configs\":"), "wrapper key must be gone: " + json);
  }

  @Test
  void hasNoResponseConfigsWhenOmitted() {
    String json =
        """
        {"url": "https://api.example.com/introspect", "method": "POST", "auth_type": "none"}
        """;

    HttpRequestExecutionConfig config = jsonConverter.read(json, HttpRequestExecutionConfig.class);

    assertFalse(config.hasResponseConfigs());
    assertTrue(config.responseResolveConfigs().isEmpty());
    assertFalse(config.toMap().containsKey("response_resolve_configs"));
  }

  @Test
  void toMapEmitsResponseResolveConfigsAsArray() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_ARRAY_FORM, HttpRequestExecutionConfig.class);

    Map<String, Object> map = config.toMap();

    assertTrue(map.containsKey("response_resolve_configs"));
    Object value = map.get("response_resolve_configs");
    assertInstanceOf(List.class, value);
    assertEquals(1, ((List<?>) value).size());
  }

  @Test
  void responseResolveConfigsRoundTripsThroughToMap() {
    HttpRequestExecutionConfig config =
        jsonConverter.read(CONFIG_WITH_ARRAY_FORM, HttpRequestExecutionConfig.class);

    String reSerialized = jsonConverter.write(config.toMap());
    HttpRequestExecutionConfig roundTripped =
        jsonConverter.read(reSerialized, HttpRequestExecutionConfig.class);

    assertTrue(roundTripped.hasResponseConfigs());
    assertEquals(
        config.responseResolveConfigs().configs().size(),
        roundTripped.responseResolveConfigs().configs().size());
    assertEquals(401, roundTripped.responseResolveConfigs().configs().get(0).mappedStatusCode());
  }
}
