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

package org.idp.server.core.extension.identity.verified;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

/**
 * OIDC4IDA §5.5.2 {@code max_age} freshness in {@link VerifiedClaimsAssembler}.
 *
 * <p>{@code max_age} is "only applicable to claims that contain dates or timestamps" and the OP
 * "should try to fulfill" it (SHOULD). A stale element is dropped individually — like §5.7.2
 * unavailable data — so the verification branch does NOT cascade to a whole {@code verified_claims}
 * omission (that cascade is reserved for the value/values MUST, §5.7.4). Elapsed time is measured
 * "from the last valid second of the date value", so a date-only value resolves to 23:59:59 of that
 * day.
 *
 * <p>Calls {@code assemble} with a fixed {@code now} so the boundaries are deterministic.
 */
class VerifiedClaimsAssemblerTest {

  private static VerifiedClaimsObject requested(String verifiedClaimsRequestJson) {
    String json = "{\"id_token\":{\"verified_claims\":" + verifiedClaimsRequestJson + "}}";
    return JsonConverter.snakeCaseInstance()
        .read(json, RequestedClaimsPayload.class)
        .idToken()
        .verifiedClaims();
  }

  private static JsonNodeWrapper userVerifiedClaims(
      Map<String, Object> verification, Map<String, Object> claims) {
    Map<String, Object> verifiedClaims = new HashMap<>();
    verifiedClaims.put("verification", verification);
    verifiedClaims.put("claims", claims);
    return new User().setVerifiedClaims(verifiedClaims).verifiedClaimsNodeWrapper();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> verificationOf(Map<String, Object> result) {
    return (Map<String, Object>)
        ((Map<String, Object>) result.get("verified_claims")).get("verification");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> claimsOf(Map<String, Object> result) {
    return (Map<String, Object>)
        ((Map<String, Object>) result.get("verified_claims")).get("claims");
  }

  @Test
  void dropsStaleTimestampVerificationElementButKeepsTheRestOfVerifiedClaims() {
    // verification.time is from 2021; now is 2026 — far older than max_age=3600s. The stale element
    // is dropped, but trust_framework (the REQUIRED floor) and the requested claim are still
    // returned: a max_age miss must NOT cascade to a whole verified_claims omission.
    Map<String, Object> verification = new HashMap<>();
    verification.put("trust_framework", "eidas");
    verification.put("time", "2021-04-09T14:12Z");
    JsonNodeWrapper user = userVerifiedClaims(verification, Map.of("given_name", "Sarah"));
    VerifiedClaimsObject request =
        requested(
            "{\"verification\":{\"trust_framework\":null,\"time\":{\"max_age\":3600}},"
                + "\"claims\":{\"given_name\":null}}");

    Map<String, Object> result =
        VerifiedClaimsAssembler.assemble(request, user, LocalDateTime.of(2026, 6, 20, 0, 0, 0));

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("eidas", verificationOf(result).get("trust_framework"));
    assertFalse(
        verificationOf(result).containsKey("time"),
        "stale verification.time must be dropped individually");
    assertEquals(
        "Sarah", claimsOf(result).get("given_name"), "verified_claims must not be whole-omitted");
  }

  @Test
  void keepsTimestampVerificationElementWithinMaxAge() {
    Map<String, Object> verification = new HashMap<>();
    verification.put("trust_framework", "eidas");
    verification.put("time", "2021-04-09T14:12Z");
    JsonNodeWrapper user = userVerifiedClaims(verification, Map.of("given_name", "Sarah"));
    // max_age huge (~31700 years) → 2021 → 2026 elapsed is well within it.
    VerifiedClaimsObject request =
        requested(
            "{\"verification\":{\"trust_framework\":null,\"time\":{\"max_age\":1000000000000}},"
                + "\"claims\":{\"given_name\":null}}");

    Map<String, Object> result =
        VerifiedClaimsAssembler.assemble(request, user, LocalDateTime.of(2026, 6, 20, 0, 0, 0));

    assertEquals("2021-04-09T14:12Z", verificationOf(result).get("time"));
  }

  @Test
  void dropsStaleDateClaimIndividually() {
    JsonNodeWrapper user =
        userVerifiedClaims(
            Map.of("trust_framework", "eidas"),
            Map.of("birthdate", "1976-03-11", "given_name", "Sarah"));
    VerifiedClaimsObject request =
        requested(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"birthdate\":{\"max_age\":3600},\"given_name\":null}}");

    Map<String, Object> result =
        VerifiedClaimsAssembler.assemble(request, user, LocalDateTime.of(2026, 6, 20, 0, 0, 0));

    assertTrue(result.containsKey("verified_claims"));
    assertFalse(claimsOf(result).containsKey("birthdate"), "stale date claim must be dropped");
    assertEquals("Sarah", claimsOf(result).get("given_name"));
  }

  @Test
  void doesNotApplyMaxAgeToNonDateClaim() {
    // §5.5.2 is "only applicable to claims that contain dates or timestamps": a non-date value is
    // never stale, so the claim is returned despite the (inapplicable) max_age.
    JsonNodeWrapper user =
        userVerifiedClaims(Map.of("trust_framework", "eidas"), Map.of("given_name", "Sarah"));
    VerifiedClaimsObject request =
        requested(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"given_name\":{\"max_age\":1}}}");

    Map<String, Object> result =
        VerifiedClaimsAssembler.assemble(request, user, LocalDateTime.of(2026, 6, 20, 0, 0, 0));

    assertEquals("Sarah", claimsOf(result).get("given_name"));
  }

  @Test
  void freshnessTakesPrecedenceOverAMatchingValueConstraint() {
    // The same claim carries both max_age (stale) and a value that DOES match the stored data.
    // Freshness is checked first, so the claim is dropped on staleness regardless of the match.
    JsonNodeWrapper user =
        userVerifiedClaims(
            Map.of("trust_framework", "eidas"),
            Map.of("birthdate", "1976-03-11", "given_name", "Sarah"));
    VerifiedClaimsObject request =
        requested(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"birthdate\":{\"max_age\":3600,\"value\":\"1976-03-11\"},"
                + "\"given_name\":null}}");

    Map<String, Object> result =
        VerifiedClaimsAssembler.assemble(request, user, LocalDateTime.of(2026, 6, 20, 0, 0, 0));

    assertFalse(
        claimsOf(result).containsKey("birthdate"),
        "stale element dropped even though its value matches");
    assertEquals("Sarah", claimsOf(result).get("given_name"));
  }

  @Test
  void measuresElapsedFromTheLastValidSecondOfADateOnlyValue() {
    // Stored date-only 2025-01-01 → last valid second 2025-01-01T23:59:59. now =
    // 2025-01-02T20:00:00
    // → elapsed ~20h, within max_age=86400s (1 day) → kept. Measuring from start-of-day (00:00:00)
    // would make elapsed ~44h and wrongly drop it, so this pins the "last valid second" rule.
    JsonNodeWrapper user =
        userVerifiedClaims(
            Map.of("trust_framework", "eidas"), Map.of("issuance_date", "2025-01-01"));
    VerifiedClaimsObject request =
        requested(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"issuance_date\":{\"max_age\":86400}}}");

    Map<String, Object> result =
        VerifiedClaimsAssembler.assemble(request, user, LocalDateTime.of(2025, 1, 2, 20, 0, 0));

    assertEquals("2025-01-01", claimsOf(result).get("issuance_date"));
  }
}
