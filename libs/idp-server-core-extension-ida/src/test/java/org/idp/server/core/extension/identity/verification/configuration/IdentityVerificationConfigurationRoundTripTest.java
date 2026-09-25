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

package org.idp.server.core.extension.identity.verification.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the management API returns has to be what it stored (Issue #1900).
 *
 * <p>The configuration is persisted by serializing the whole object, but read back through {@code
 * toMap}, and the two had drifted: {@code result} was emitted only when {@code
 * verified_claims_mapping_rules} was non-empty, and {@code registration} dropped {@code response}
 * entirely while emitting {@code basic_auth} as an object rather than its map. Everything was in
 * the database and none of it came back, so a caller who read a configuration, edited one field and
 * wrote the whole thing back silently erased the rest.
 *
 * <p>The round trip is asserted here rather than through an E2E because the loss is invisible at
 * the API surface: the write succeeds and answers 200 with the impoverished body it just produced.
 */
class IdentityVerificationConfigurationRoundTripTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private IdentityVerificationConfiguration read(Map<String, Object> payload) {
    return jsonConverter.read(payload, IdentityVerificationConfiguration.class);
  }

  @Test
  @DisplayName("verified_claims_mapping_rules が無くても result が返る")
  void resultSurvivesWithoutVerifiedClaimsMappingRules() {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f001",
            "type",
            "probe",
            "result",
            Map.of(
                "user_status",
                "KEEP",
                "user_claims_mapping_rules",
                List.of(Map.of("from", "$.application.application_details.email", "to", "email"))));

    Map<String, Object> roundTripped = read(payload).toMap();

    assertTrue(roundTripped.containsKey("result"), "result was dropped");
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) roundTripped.get("result");
    assertEquals("KEEP", result.get("user_status"));
    assertEquals(1, ((List<?>) result.get("user_claims_mapping_rules")).size());
  }

  @Test
  @DisplayName("custom_properties / 更新方針だけでも result が返る")
  void resultSurvivesWithOnlyUpdatePolicies() {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f002",
            "type",
            "probe",
            "result",
            Map.of(
                "verified_claims_update_policy",
                "deep_merge",
                "custom_properties_update_policy",
                "replace_managed"));

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) read(payload).toMap().get("result");

    assertNotNull(result, "result was dropped");
    assertEquals("deep_merge", result.get("verified_claims_update_policy"));
    assertEquals("replace_managed", result.get("custom_properties_update_policy"));
  }

  @Test
  @DisplayName("何も設定されていない result は返さない")
  void emptyResultIsOmitted() {
    Map<String, Object> payload =
        Map.of("id", "0198e1c0-0000-7000-8000-00000000f003", "type", "probe", "result", Map.of());

    assertFalse(read(payload).toMap().containsKey("result"));
  }

  @Test
  @DisplayName("registration.response と basic_auth が返る")
  void registrationKeepsResponseAndBasicAuth() {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f004",
            "type",
            "probe",
            "registration",
            Map.of(
                "basic_auth",
                Map.of("username", "u1", "password", "p1"),
                "response",
                Map.of("body_mapping_rules", List.of(Map.of("from", "$.x", "to", "y")))));

    @SuppressWarnings("unchecked")
    Map<String, Object> registration =
        (Map<String, Object>) read(payload).toMap().get("registration");

    assertNotNull(registration);
    @SuppressWarnings("unchecked")
    Map<String, Object> basicAuth = (Map<String, Object>) registration.get("basic_auth");
    assertEquals("u1", basicAuth.get("username"));
    assertEquals("p1", basicAuth.get("password"));

    @SuppressWarnings("unchecked")
    Map<String, Object> response = (Map<String, Object>) registration.get("response");
    assertNotNull(response, "registration.response was dropped");
    assertEquals(1, ((List<?>) response.get("body_mapping_rules")).size());
  }

  @Test
  @DisplayName("toMap をそのまま読み直しても失われない")
  void toMapIsReadableBackWithoutLoss() {
    Map<String, Object> payload =
        Map.of(
            "id",
            "0198e1c0-0000-7000-8000-00000000f005",
            "type",
            "probe",
            "enabled",
            false,
            "result",
            Map.of(
                "user_status",
                "KEEP",
                "user_claims_mapping_rules",
                List.of(Map.of("from", "$.a", "to", "email"))),
            "registration",
            Map.of(
                "response",
                Map.of("body_mapping_rules", List.of(Map.of("from", "$.x", "to", "y")))));

    Map<String, Object> once = read(payload).toMap();
    Map<String, Object> twice = read(once).toMap();

    assertEquals(once, twice, "a second round trip changed the configuration");
    assertEquals(false, twice.get("enabled"));
  }
}
