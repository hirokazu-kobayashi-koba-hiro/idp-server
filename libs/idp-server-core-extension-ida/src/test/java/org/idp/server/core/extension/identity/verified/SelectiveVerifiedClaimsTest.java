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

import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.junit.jupiter.api.Test;

/**
 * Regression for the OIDC4IDA selective verified_claims output.
 *
 * <p>Key case: when the requested {@code verified_claims:*} scopes match no user claim, nothing
 * must be emitted — emitting {@code verified_claims} with an empty {@code claims} object would leak
 * the {@code verification} metadata (trust_framework / evidence) without any actual verified claim.
 */
class SelectiveVerifiedClaimsTest {

  private static JsonNodeWrapper userVerifiedClaims() {
    return JsonNodeWrapper.fromMap(
        Map.of(
            "verification", Map.of("trust_framework", "eidas"),
            "claims", Map.of("given_name", "Taro", "family_name", "Yamada")));
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

  @Test
  void buildReturnsOnlyRequestedClaimsWithVerification() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("openid", "verified_claims:given_name"), userVerifiedClaims());

    assertTrue(result.containsKey("verified_claims"));
    Map<String, Object> claims = claimsOf(result);
    assertEquals("Taro", claims.get("given_name"));
    assertFalse(claims.containsKey("family_name"), "non-requested claim must not be included");

    Map<String, Object> verification = (Map<String, Object>) structure(result).get("verification");
    assertEquals("eidas", verification.get("trust_framework"));
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
  void buildReturnsEmptyWhenNoRequestedClaimMatches() {
    // #1514 review fix: must NOT leak verification with an empty claims object.
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(scopes("verified_claims:nonexistent"), userVerifiedClaims());

    assertTrue(result.isEmpty(), "no matching claim must emit no verified_claims at all");
  }

  @Test
  void buildIgnoresUnmatchedRequestedClaims() {
    Map<String, Object> result =
        SelectiveVerifiedClaims.build(
            scopes("verified_claims:given_name", "verified_claims:nonexistent"),
            userVerifiedClaims());

    Map<String, Object> claims = claimsOf(result);
    assertEquals(Set.of("given_name"), claims.keySet());
  }

  @Test
  void buildReturnsEmptyVerificationWhenSourceHasNone() {
    JsonNodeWrapper noVerification =
        JsonNodeWrapper.fromMap(Map.of("claims", Map.of("given_name", "Taro")));

    Map<String, Object> result =
        SelectiveVerifiedClaims.build(scopes("verified_claims:given_name"), noVerification);

    assertTrue(result.containsKey("verified_claims"));
    Map<String, Object> verification = (Map<String, Object>) structure(result).get("verification");
    assertTrue(verification.isEmpty());
  }

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
  void hasSelectableClaimsFalseWhenNoClaimsNode() {
    JsonNodeWrapper onlyVerification =
        JsonNodeWrapper.fromMap(Map.of("verification", Map.of("trust_framework", "eidas")));

    assertFalse(
        SelectiveVerifiedClaims.hasSelectableClaims(
            scopes("verified_claims:given_name"), onlyVerification));
  }
}
