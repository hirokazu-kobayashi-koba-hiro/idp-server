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
import org.junit.jupiter.api.Test;

/**
 * Backport verification: a v0.10.x app must be able to read {@code response_resolve_configs}
 * persisted by v0.12.0+ in the bare-array form (#1500), while still reading the legacy
 * object-wrapper form it writes itself.
 */
class HttpResponseResolveConfigsDeserializerTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  void reads_new_bare_array_form() {
    // The v0.12.0+ persisted form that crashed v0.10.1 with MismatchedInputException.
    String json = "[{\"conditions\":[],\"match_mode\":\"ALL\",\"mapped_status_code\":200}]";

    HttpResponseResolveConfigs configs = jsonConverter.read(json, HttpResponseResolveConfigs.class);

    assertEquals(1, configs.configs().size());
    assertEquals(200, configs.configs().get(0).mappedStatusCode());
  }

  @Test
  void reads_legacy_object_wrapper_form() {
    // The pre-#1500 form this app writes itself; must still round-trip.
    String json =
        "{\"configs\":[{\"conditions\":[],\"match_mode\":\"ALL\",\"mapped_status_code\":503}]}";

    HttpResponseResolveConfigs configs = jsonConverter.read(json, HttpResponseResolveConfigs.class);

    assertEquals(1, configs.configs().size());
    assertEquals(503, configs.configs().get(0).mappedStatusCode());
  }

  @Test
  void reads_bare_array_nested_in_http_request_execution_config() {
    // Mirrors the real failure chain: HttpRequestExecutionConfig["response_resolve_configs"].
    String json =
        "{\"http_method\":\"POST\",\"http_request_url\":\"https://example.com/webhook\","
            + "\"response_resolve_configs\":[{\"conditions\":[],\"match_mode\":\"ALL\","
            + "\"mapped_status_code\":200}]}";

    // Previously this exact field threw MismatchedInputException ("from Array value");
    // succeeding without throwing is the fix.
    HttpRequestExecutionConfig config = jsonConverter.read(json, HttpRequestExecutionConfig.class);

    assertNotNull(config);
  }

  @Test
  void empty_or_absent_yields_empty_configs() {
    HttpResponseResolveConfigs fromEmptyArray =
        jsonConverter.read("[]", HttpResponseResolveConfigs.class);
    assertTrue(fromEmptyArray.isEmpty());
  }
}
