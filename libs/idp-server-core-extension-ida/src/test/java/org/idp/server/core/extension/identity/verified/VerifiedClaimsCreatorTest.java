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
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * Regression for the OIDC4IDA verified_claims output in the ID Token ({@code claims} parameter)
 * path.
 *
 * <p>OIDC4IDA §5.7.4 / §5.7.5: when the {@code verification} requirement cannot be met (no required
 * {@code trust_framework}) or no requested claim matches a user claim, the OP SHALL omit the whole
 * {@code verified_claims} element rather than emit a schema-invalid {@code verification: {}} or an
 * empty {@code claims} object. This mirrors {@link SelectiveVerifiedClaims} (scope-based path).
 */
class VerifiedClaimsCreatorTest {

  private final VerifiedClaimsCreator creator = new VerifiedClaimsCreator();

  /** Builds the RP request payload from the {@code verified_claims} request object (snake_case). */
  private static RequestedClaimsPayload request(String verifiedClaimsRequestJson) {
    String json = "{\"id_token\":{\"verified_claims\":" + verifiedClaimsRequestJson + "}}";
    return JsonConverter.snakeCaseInstance().read(json, RequestedClaimsPayload.class);
  }

  private static User userWith(Map<String, Object> verification, Map<String, Object> claims) {
    Map<String, Object> verifiedClaims = new HashMap<>();
    verifiedClaims.put("verification", verification);
    verifiedClaims.put("claims", claims);
    return new User().setVerifiedClaims(verifiedClaims);
  }

  private Map<String, Object> create(User user, RequestedClaimsPayload payload) {
    return creator.create(user, null, null, null, payload, null, null);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> structure(Map<String, Object> result) {
    return (Map<String, Object>) result.get("verified_claims");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> verificationOf(Map<String, Object> result) {
    return (Map<String, Object>) structure(result).get("verification");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> claimsOf(Map<String, Object> result) {
    return (Map<String, Object>) structure(result).get("claims");
  }

  @Test
  void omitsVerifiedClaimsWhenUserHasNoTrustFramework() {
    // #1512 repro: RP requests verification.trust_framework + a claim, but the user's stored
    // verification has no trust_framework. §5.7.4: omit verified_claims entirely (must NOT return
    // {"verified_claims": {"verification": {}, "claims": {...}}}).
    User user = userWith(new HashMap<>(), Map.of("given_name", "Taro"));
    RequestedClaimsPayload payload =
        request("{\"verification\":{\"trust_framework\":null},\"claims\":{\"given_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertFalse(
        result.containsKey("verified_claims"),
        "verification requirement unmet (no trust_framework) must emit no verified_claims");
  }

  @Test
  void emitsVerifiedClaimsWhenVerificationAndClaimsPresent() {
    User user =
        userWith(
            Map.of("trust_framework", "eidas"),
            Map.of("given_name", "Taro", "family_name", "Yamada"));
    RequestedClaimsPayload payload =
        request(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"given_name\":null,\"family_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("eidas", verificationOf(result).get("trust_framework"));
    assertEquals("Taro", claimsOf(result).get("given_name"));
    assertEquals("Yamada", claimsOf(result).get("family_name"));
  }

  @Test
  void omitsVerifiedClaimsWhenNoRequestedClaimMatches() {
    // §5.7.5: parent (verified_claims) requires claims; with no matching claim, omit the parent
    // rather than emit verification with an empty claims object.
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("family_name", "Yamada"));
    RequestedClaimsPayload payload =
        request("{\"verification\":{\"trust_framework\":null},\"claims\":{\"given_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertFalse(
        result.containsKey("verified_claims"),
        "no matching claim must emit no verified_claims at all");
  }

  @Test
  void omitsVerifiedClaimsWhenVerificationHasOnlyEvidenceWithoutTrustFramework() {
    // Guards the trust_framework check (vs. a plain verification.isEmpty() check): the user's
    // verification carries evidence but no trust_framework, so verification is non-empty yet
    // schema-invalid. §5.7.4: still omit verified_claims.
    Map<String, Object> verification = new HashMap<>();
    verification.put("evidence", List.of(Map.of("type", "electronic_record")));
    User user = userWith(verification, Map.of("given_name", "Taro"));
    RequestedClaimsPayload payload =
        request(
            "{\"verification\":{\"trust_framework\":null,\"evidence\":null},"
                + "\"claims\":{\"given_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertFalse(
        result.containsKey("verified_claims"),
        "verification without the required trust_framework must emit no verified_claims");
  }
}
