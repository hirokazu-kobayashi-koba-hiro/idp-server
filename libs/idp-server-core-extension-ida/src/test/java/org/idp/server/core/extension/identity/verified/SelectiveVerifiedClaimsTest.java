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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

/**
 * Regression for the OIDC4IDA selective verified_claims output.
 *
 * <p>Both blocks are selected per scope: {@code verified_claims:<claim>} for verified claims and
 * {@code verified_claims:verification:<element>} for verification elements. Names are looked up
 * dynamically against the user's stored verified_claims, and sensitive verification data (e.g.
 * {@code evidence}) is returned only when its scope is explicitly requested. When no requested
 * claim matches, nothing is emitted (§5.7.4).
 */
class SelectiveVerifiedClaimsTest {

  private static JsonNodeWrapper userVerifiedClaims() {
    Map<String, Object> verification = new HashMap<>();
    verification.put("trust_framework", "eidas");
    verification.put("evidence", List.of(Map.of("type", "electronic_record")));

    Map<String, Object> claims = new HashMap<>();
    claims.put("given_name", "Taro");
    claims.put("family_name", "Yamada");

    Map<String, Object> verifiedClaims = new HashMap<>();
    verifiedClaims.put("verification", verification);
    verifiedClaims.put("claims", claims);
    return JsonNodeWrapper.fromMap(verifiedClaims);
  }

  private static Scopes scopes(String... values) {
    return new Scopes(Set.of(values));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> structure(Map<String, Object> result) {
    return (Map<String, Object>) result.get("verified_claims");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> claimsOf(Map<String, Object> result) {
    return (Map<String, Object>) structure(result).get("claims");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> verificationOf(Map<String, Object> result) {
    return (Map<String, Object>) structure(result).get("verification");
  }

  // --- claims selection (verified_claims:<claim>) ---

  @Test
  void buildReturnsOnlyRequestedClaims() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("openid", "verified_claims:given_name"), userVerifiedClaims());

    assertTrue(result.containsKey("verified_claims"));
    Map<String, Object> claims = claimsOf(result);
    assertEquals("Taro", claims.get("given_name"));
    assertFalse(claims.containsKey("family_name"), "non-requested claim must not be included");
  }

  @Test
  void buildIncludesMultipleRequestedClaims() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:family_name"),
            userVerifiedClaims());

    Map<String, Object> claims = claimsOf(result);
    assertEquals("Taro", claims.get("given_name"));
    assertEquals("Yamada", claims.get("family_name"));
  }

  @Test
  void buildIgnoresUnmatchedRequestedClaims() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:nonexistent"),
            userVerifiedClaims());

    assertEquals(Set.of("given_name"), claimsOf(result).keySet());
  }

  @Test
  void buildReturnsEmptyWhenNoRequestedClaimMatches() {
    // §5.7.4: must NOT leak verification with an empty claims object.
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(scopes("verified_claims:nonexistent"), userVerifiedClaims());

    assertTrue(result.isEmpty(), "no matching claim must emit no verified_claims at all");
  }

  // --- verification selection (verified_claims:verification:<element>) ---

  @Test
  void buildIncludesRequestedVerificationElement() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:verification:trust_framework"),
            userVerifiedClaims());

    assertEquals("eidas", verificationOf(result).get("trust_framework"));
  }

  @Test
  void buildExcludesEvidenceUnlessRequested() {
    // evidence carries sensitive PII; requesting only trust_framework must not return evidence.
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:verification:trust_framework"),
            userVerifiedClaims());

    Map<String, Object> verification = verificationOf(result);
    assertTrue(verification.containsKey("trust_framework"));
    assertFalse(verification.containsKey("evidence"), "evidence must be opt-in via its own scope");
  }

  @Test
  void buildIncludesEvidenceWhenRequested() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:verification:evidence"),
            userVerifiedClaims());

    assertTrue(verificationOf(result).containsKey("evidence"));
  }

  @Test
  void buildOmitsVerificationContentWhenNotRequested() {
    // claims requested but no verification scope: verified_claims is emitted, verification is
    // empty.
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(scopes("verified_claims:given_name"), userVerifiedClaims());

    assertTrue(result.containsKey("verified_claims"));
    assertTrue(verificationOf(result).isEmpty(), "verification must not leak without its scope");
  }

  @Test
  void buildIgnoresUnmatchedVerificationElement() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:verification:nonexistent"),
            userVerifiedClaims());

    assertTrue(verificationOf(result).isEmpty());
  }

  @Test
  void buildReturnsEmptyWhenOnlyVerificationRequested() {
    // verification scope without any matching claim: §5.7.4 — emit nothing.
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:verification:trust_framework"), userVerifiedClaims());

    assertTrue(result.isEmpty());
  }

  @Test
  void buildHandlesNullVerification() {
    // "verification": null must not blow up; it is treated as empty.
    JsonNodeWrapper nullVerification =
        JsonNodeWrapper.fromString("{\"verification\":null,\"claims\":{\"given_name\":\"Taro\"}}");

    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:verification:trust_framework"),
            nullVerification);

    assertTrue(result.containsKey("verified_claims"));
    assertTrue(verificationOf(result).isEmpty());
  }

  // --- hasSelectableClaims ---

  @Test
  void hasSelectableClaimsTrueWhenRequestedClaimMatches() {
    assertTrue(
        SelectiveVerifiedClaims.hasSelectableClaims(
            scopes("verified_claims:given_name"), userVerifiedClaims()));
  }

  @Test
  void hasSelectableClaimsFalseWhenNoMatch() {
    assertFalse(
        SelectiveVerifiedClaims.hasSelectableClaims(
            scopes("verified_claims:nonexistent"), userVerifiedClaims()));
  }

  @Test
  void hasSelectableClaimsFalseWhenOnlyVerificationRequested() {
    // verification scopes must not count as selectable claims.
    assertFalse(
        SelectiveVerifiedClaims.hasSelectableClaims(
            scopes("verified_claims:verification:trust_framework"), userVerifiedClaims()));
  }

  @Test
  void hasSelectableClaimsFalseWhenNoClaimsNode() {
    JsonNodeWrapper onlyVerification =
        JsonNodeWrapper.fromMap(Map.of("verification", Map.of("trust_framework", "eidas")));

    assertFalse(
        SelectiveVerifiedClaims.hasSelectableClaims(
            scopes("verified_claims:given_name"), onlyVerification));
  }
}
