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

package org.idp.server.platform.multi_tenancy.tenant.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tenant cache writes a {@code Tenant} with {@code JsonConverter.write} and reads it back with
 * {@code JsonConverter.read} ({@code JedisCacheStore}), so every nested configuration has to
 * survive a Jackson round trip.
 *
 * <p>This is not reachable from an E2E: a failed read is caught, logged and falls back to the
 * database, so the request still answers 200 while the cache is silently dead and the log fills up.
 * The round trip has to be asserted directly.
 */
class ContactChangePolicyConfigJsonRoundTripTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  @DisplayName("default policy survives a Jackson round trip")
  void defaultPolicyRoundTrips() {
    TenantIdentityPolicy policy =
        TenantIdentityPolicy.fromMap(
            Map.of("identity_unique_key_type", "EMAIL", "contact_change_policy", Map.of()));

    TenantIdentityPolicy restored = roundTrip(policy);

    assertEquals(
        IdentityVerifiedBehavior.DENY,
        restored.contactChangePolicy().identifierMove().identityVerifiedBehavior());
    assertEquals(
        IdentityVerifiedBehavior.ALLOW,
        restored.contactChangePolicy().attributeOnly().identityVerifiedBehavior());
    assertTrue(restored.contactChangePolicy().identifierMove().shouldNotifyPreviousValue());
  }

  @Test
  @DisplayName("a configured policy survives a Jackson round trip")
  void configuredPolicyRoundTrips() {
    TenantIdentityPolicy policy =
        TenantIdentityPolicy.fromMap(
            Map.of(
                "identity_unique_key_type",
                "EMAIL",
                "contact_change_policy",
                Map.of(
                    "identifier_move",
                    Map.of(
                        "authentication_conditions",
                        Map.of(
                            "any_of",
                            List.of(
                                List.of(
                                    Map.of(
                                        "path",
                                        "$.amr",
                                        "operation",
                                        "contains",
                                        "value",
                                        "password")))),
                        "max_auth_age_seconds",
                        300,
                        "notify_previous_value",
                        false,
                        "identity_verified_behavior",
                        "DENY"))));

    TenantIdentityPolicy restored = roundTrip(policy);
    ContactChangeRule identifierMove = restored.contactChangePolicy().identifierMove();

    assertTrue(identifierMove.hasAuthenticationConditions());
    assertEquals(300, identifierMove.maxAuthAgeSeconds());
    assertFalse(identifierMove.shouldNotifyPreviousValue());
    assertEquals(IdentityVerifiedBehavior.DENY, identifierMove.identityVerifiedBehavior());
  }

  /**
   * An unreadable condition block must still fail closed after the round trip: the flag is what
   * keeps a typo from quietly becoming "no requirement", so losing it in the cache would hand back
   * a permissive rule.
   */
  @Test
  @DisplayName("an unreadable condition block stays unsatisfiable across a round trip")
  void malformedConditionsStayClosed() {
    TenantIdentityPolicy policy =
        TenantIdentityPolicy.fromMap(
            Map.of(
                "identity_unique_key_type",
                "EMAIL",
                "contact_change_policy",
                Map.of(
                    "identifier_move",
                    Map.of(
                        "authentication_conditions",
                        Map.of(
                            "any_of",
                            List.of(
                                List.of(
                                    Map.of("path", "$.amr", "operation", "not-an-operation"))))))));

    assertFalse(policy.contactChangePolicy().identifierMove().satisfiedBy(null));

    TenantIdentityPolicy restored = roundTrip(policy);

    assertFalse(restored.contactChangePolicy().identifierMove().satisfiedBy(null));
  }

  private TenantIdentityPolicy roundTrip(TenantIdentityPolicy policy) {
    String json = jsonConverter.write(policy);
    return jsonConverter.read(json, TenantIdentityPolicy.class);
  }
}
