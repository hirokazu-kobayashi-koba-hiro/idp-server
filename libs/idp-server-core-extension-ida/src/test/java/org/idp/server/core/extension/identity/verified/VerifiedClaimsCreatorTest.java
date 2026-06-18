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
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.grant_management.grant.RequestedVerifiedClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * Regression for the OIDC4IDA verified_claims output in the ID Token ({@code claims} parameter)
 * path.
 *
 * <p>OIDC4IDA §5.7.4 / §5.7.5: the whole {@code verified_claims} element is omitted only when the
 * {@code verification} requirement cannot be met — a failed {@code verification} {@code value} /
 * {@code values} constraint, or a missing required {@code trust_framework} — never emitting a
 * schema-invalid {@code verification: {}}. When every requested claim drops but {@code
 * verification} is valid, the element is kept with an empty {@code claims} object, which the IDA
 * schema permits ([IDA-verified-claims] §5.3, "the claims element may be empty").
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
    // Production persists the id_token.verified_claims request in the grant (the consent record),
    // and the creator reads it from there. Build a grant carrying that request.
    GrantIdTokenClaims idTokenClaims =
        new GrantIdTokenClaims(
            Set.of(), RequestedVerifiedClaims.of(payload.idToken().verifiedClaims()));
    AuthorizationGrant grant =
        new AuthorizationGrantBuilder(
                new TenantIdentifier("11111111-1111-1111-1111-111111111111"),
                new RequestedClientId("client"),
                GrantType.authorization_code,
                new Scopes(Set.of("openid")))
            .add(user)
            .add(idTokenClaims)
            .build();
    return creator.create(user, null, grant, null, payload, null, null);
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
  void keepsVerifiedClaimsWithEmptyClaimsWhenNoRequestedClaimMatches() {
    // §5.7.2 drops the unavailable claim individually. The IDA schema ([IDA-verified-claims] §5.3)
    // permits an empty claims object, so verification + an empty claims object is a valid response
    // —
    // NOT a whole omission. §5.7.5 cascades only when the omitted element is required for validity,
    // which an empty claims object is not.
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("family_name", "Yamada"));
    RequestedClaimsPayload payload =
        request("{\"verification\":{\"trust_framework\":null},\"claims\":{\"given_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("eidas", verificationOf(result).get("trust_framework"));
    assertTrue(
        claimsOf(result).isEmpty(),
        "unavailable claim dropped; claims object left empty, not omitted");
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

  // ----- value/values constraint enforcement, dynamic claims, trust_framework user-gate (#1624) --

  @Test
  void emitsWhenValueAndValuesConstraintsMatch() {
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    // verification value + claims values both satisfied by the stored data.
    RequestedClaimsPayload payload =
        request(
            "{\"verification\":{\"trust_framework\":{\"value\":\"eidas\"}},"
                + "\"claims\":{\"given_name\":{\"values\":[\"Hanako\",\"Taro\"]}}}");

    Map<String, Object> result = create(user, payload);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("eidas", verificationOf(result).get("trust_framework"));
    assertEquals("Taro", claimsOf(result).get("given_name"));
  }

  @Test
  void omitsVerifiedClaimsWhenVerificationValueDoesNotMatch() {
    // §5.7.4 (verification branch): requested trust_framework value "gold" but the user has "eidas"
    // → the verification requirement is unmet, so the whole verified_claims is omitted.
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    RequestedClaimsPayload payload =
        request(
            "{\"verification\":{\"trust_framework\":{\"value\":\"gold\"}},"
                + "\"claims\":{\"given_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertFalse(
        result.containsKey("verified_claims"),
        "verification value mismatch must omit the whole verified_claims");
  }

  @Test
  void dropsOnlyTheClaimWhoseValueDoesNotMatch() {
    // §5.7.4 (claims branch): given_name value mismatch drops just given_name; family_name (no
    // constraint) is still returned, so verified_claims itself is kept.
    User user =
        userWith(
            Map.of("trust_framework", "eidas"),
            Map.of("given_name", "Sarah", "family_name", "Yamada"));
    RequestedClaimsPayload payload =
        request(
            "{\"verification\":{\"trust_framework\":null},"
                + "\"claims\":{\"given_name\":{\"value\":\"Bob\"},\"family_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("Yamada", claimsOf(result).get("family_name"));
    assertFalse(
        claimsOf(result).containsKey("given_name"),
        "claim failing its value constraint must be dropped individually");
  }

  @Test
  void returnsDynamicallyResolvedClaimBeyondLegacyFixedList() {
    // place_of_birth is not in the old hard-coded list; the dynamic resolver returns it when the
    // user holds it.
    User user =
        userWith(
            Map.of("trust_framework", "eidas"), Map.of("place_of_birth", Map.of("country", "UK")));
    RequestedClaimsPayload payload =
        request(
            "{\"verification\":{\"trust_framework\":null},\"claims\":{\"place_of_birth\":null}}");

    Map<String, Object> result = create(user, payload);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals(Map.of("country", "UK"), claimsOf(result).get("place_of_birth"));
  }

  @Test
  void alwaysReturnsTrustFrameworkEvenWhenRequestOmitsIt() {
    // trust_framework is the REQUIRED floor: returned whenever the user holds it, even if the RP
    // did
    // not list it (no verification block in the request).
    User user = userWith(Map.of("trust_framework", "eidas"), Map.of("given_name", "Taro"));
    RequestedClaimsPayload payload = request("{\"claims\":{\"given_name\":null}}");

    Map<String, Object> result = create(user, payload);

    assertTrue(result.containsKey("verified_claims"));
    assertEquals("eidas", verificationOf(result).get("trust_framework"));
    assertEquals("Taro", claimsOf(result).get("given_name"));
  }
}
