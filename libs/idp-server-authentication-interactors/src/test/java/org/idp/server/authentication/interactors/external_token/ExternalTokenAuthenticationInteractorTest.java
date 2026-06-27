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

package org.idp.server.authentication.interactors.external_token;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.mapper.MappingRule;
import org.junit.jupiter.api.Test;

/** Verifies Issue #1696: external-token applies {@code response.body_mapping_rules}. */
class ExternalTokenAuthenticationInteractorTest {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  ExternalTokenAuthenticationInteractor interactor =
      new ExternalTokenAuthenticationInteractor(null, null);

  @Test
  void appliesResponseBodyMappingRulesAndKeepsUserEnvelope() {
    // A config whose external-token interaction declares response.body_mapping_rules.
    // Before the fix these rules were parsed but never applied.
    String configJson =
        """
        {
          "id": "5ee62e7d-e1f0-4f38-b714-0c5d6a28d8f1",
          "type": "external-token",
          "interactions": {
            "external-token": {
              "execution": { "function": "http_requests", "http_requests": [] },
              "user_resolve": { "user_mapping_rules": [] },
              "response": {
                "body_mapping_rules": [
                  { "from": "$.execution_http_requests[0].response_body.email_verified", "to": "email_verified" },
                  { "from": "$.request_body.access_token", "to": "echoed_token" }
                ]
              }
            }
          }
        }
        """;
    AuthenticationConfiguration configuration =
        jsonConverter.read(configJson, AuthenticationConfiguration.class);
    List<MappingRule> rules =
        configuration.getAuthenticationConfig("external-token").response().bodyMappingRules();
    assertEquals(2, rules.size(), "response.body_mapping_rules must be parsed from config");

    // Mapping source as built in interact(): request_body + flattened execution results.
    Map<String, Object> executionResponseBody = new HashMap<>();
    executionResponseBody.put("email_verified", true);
    Map<String, Object> executionStep0 = new HashMap<>();
    executionStep0.put("response_body", executionResponseBody);
    Map<String, Object> mappingSource = new HashMap<>();
    mappingSource.put("request_body", Map.of("access_token", "tok-123"));
    mappingSource.put("execution_http_requests", List.of(executionStep0));

    User user = jsonConverter.read(Map.of("sub", "sub-xyz", "status", "INITIALIZED"), User.class);

    Map<String, Object> result = interactor.toResponseContents(rules, mappingSource, user);

    // Mapped fields are now surfaced (previously silently dropped).
    assertEquals(true, result.get("email_verified"));
    assertEquals("tok-123", result.get("echoed_token"));

    // Backward compatibility: the minimalized user envelope is retained.
    assertInstanceOf(Map.class, result.get("user"));
    @SuppressWarnings("unchecked")
    Map<String, Object> userMap = (Map<String, Object>) result.get("user");
    assertEquals("sub-xyz", userMap.get("sub"));
    assertEquals("INITIALIZED", userMap.get("status"));
  }

  @Test
  void keepsLegacyUserOnlyEnvelopeWhenNoRulesConfigured() {
    User user = jsonConverter.read(Map.of("sub", "sub-1", "status", "INITIALIZED"), User.class);

    Map<String, Object> result =
        interactor.toResponseContents(new ArrayList<>(), new HashMap<>(), user);

    assertEquals(
        Set.of("user"), result.keySet(), "no rules configured -> unchanged {user} envelope");
  }
}
