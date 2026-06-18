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

package org.idp.server.core.openid.grant_management.grant;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.RequestedIdTokenClaims;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.oauth.type.oauth.ResponseType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression for #1594.
 *
 * <p>{@code id_token_strict_mode=true} では、各 {@code shouldAddXxx} は scope ではなく {@code claims}
 * リクエストパラメータの {@code hasXxx()} を参照する。コピペで別クレームの has() を参照していると、strict mode で
 * クレームの出し分けが無関係なクレームの要求有無に連動してしまう。
 *
 * <p>表駆動で「クレーム X を単独要求 → 出力が {X} だけ」を全標準クレームについて検証する。
 */
class GrantIdTokenClaimsTest {

  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  /** ID Token で scope/claims から出し分けされる標準クレーム一覧。 */
  private static final List<String> STANDARD_CLAIMS =
      List.of(
          "name",
          "given_name",
          "family_name",
          "middle_name",
          "nickname",
          "preferred_username",
          "profile",
          "picture",
          "website",
          "email",
          "email_verified",
          "gender",
          "birthdate",
          "zoneinfo",
          "locale",
          "phone_number",
          "phone_number_verified",
          "address",
          "updated_at");

  static Stream<String> standardClaims() {
    return STANDARD_CLAIMS.stream();
  }

  private static RequestedIdTokenClaims requestOnly(String claim) {
    return JSON.read("{\"" + claim + "\":{\"essential\":true}}", RequestedIdTokenClaims.class);
  }

  @ParameterizedTest(name = "strict mode: request only [{0}] -> grant id_token claims == [{0}]")
  @MethodSource("standardClaims")
  void strictModeClaimsParameterMapsEachClaimToItself(String claim) {
    GrantIdTokenClaims result =
        GrantIdTokenClaims.create(
            new Scopes(), ResponseType.code, STANDARD_CLAIMS, requestOnly(claim), true);

    assertEquals(
        Set.of(claim),
        result.toStringSet(),
        "strict mode: requesting only '" + claim + "' must yield exactly that claim");
  }

  @Test
  void nonStrictProfileScopeIncludesFamilyName() {
    GrantIdTokenClaims result =
        GrantIdTokenClaims.create(
            new Scopes(Set.of("profile")),
            ResponseType.code,
            STANDARD_CLAIMS,
            new RequestedIdTokenClaims(),
            false);

    assertTrue(result.contains("name"));
    assertTrue(result.contains("family_name"));
    assertTrue(result.contains("website"));
  }

  @Test
  void strictModeWithoutClaimsParameterExcludesScopeBasedClaims() {
    // strict mode では scope だけではクレームは付与されない（claims パラメータが必須）
    GrantIdTokenClaims result =
        GrantIdTokenClaims.create(
            new Scopes(Set.of("profile")),
            ResponseType.code,
            STANDARD_CLAIMS,
            new RequestedIdTokenClaims(),
            true);

    assertFalse(result.contains("family_name"));
    assertFalse(result.contains("name"));
  }

  @Test
  void requestedClaimNotInSupportedIsExcluded() {
    List<String> supportedWithoutFamilyName =
        STANDARD_CLAIMS.stream().filter(c -> !c.equals("family_name")).toList();

    GrantIdTokenClaims result =
        GrantIdTokenClaims.create(
            new Scopes(),
            ResponseType.code,
            supportedWithoutFamilyName,
            requestOnly("family_name"),
            true);

    assertFalse(result.contains("family_name"));
  }

  // ---- #1628 follow-up: verified_claims request persisted with the grant (sentinel round-trip) --

  private static VerifiedClaimsObject sampleVerifiedClaimsRequest() {
    return new VerifiedClaimsObject(
        Map.of("trust_framework", Map.of("value", "eidas")), Map.of("given_name", Map.of()));
  }

  @Test
  void roundTripsVerifiedClaimsThroughStringForm() {
    GrantIdTokenClaims original =
        new GrantIdTokenClaims(
            Set.of("name"), RequestedVerifiedClaims.of(sampleVerifiedClaimsRequest()));

    GrantIdTokenClaims restored = new GrantIdTokenClaims(original.toStringValues());

    assertTrue(restored.hasVerifiedClaims());
    assertTrue(restored.hasName());
    assertTrue(restored.verifiedClaims().verificationNodeWrapper().contains("trust_framework"));
    assertTrue(restored.verifiedClaims().claimsNodeWrapper().contains("given_name"));
  }

  @Test
  void recognizesLegacyVerifiedClaimsNameToken() {
    // A row written before this change carries the bare "verified_claims" name token, not a
    // sentinel. hasVerifiedClaims() must still return true; verifiedClaims() returns null so the
    // creator falls back to the live request.
    GrantIdTokenClaims legacy = new GrantIdTokenClaims("given_name verified_claims");

    assertTrue(legacy.hasVerifiedClaims());
    assertNull(legacy.verifiedClaims());
    assertTrue(legacy.hasGivenName());
  }

  @Test
  void backwardCompatibleWithLegacyNameOnlyString() {
    GrantIdTokenClaims legacy = new GrantIdTokenClaims("name email");

    assertFalse(legacy.hasVerifiedClaims());
    assertTrue(legacy.hasName());
    assertTrue(legacy.hasEmail());
  }

  @Test
  void sentinelDoesNotLeakIntoPlainClaimNameView() {
    GrantIdTokenClaims claims =
        new GrantIdTokenClaims(
            Set.of("name"), RequestedVerifiedClaims.of(sampleVerifiedClaimsRequest()));

    // toStringSet()/iterator() expose claim names only — never the vc: sentinel.
    assertEquals(Set.of("name"), claims.toStringSet());
    for (String name : claims) {
      assertFalse(name.startsWith(RequestedVerifiedClaims.SENTINEL_PREFIX));
    }
  }

  @Test
  void existsWhenOnlyVerifiedClaimsRequested() {
    GrantIdTokenClaims claims =
        new GrantIdTokenClaims(Set.of(), RequestedVerifiedClaims.of(sampleVerifiedClaimsRequest()));

    assertTrue(claims.exists());
    assertTrue(claims.toStringValues().startsWith(RequestedVerifiedClaims.SENTINEL_PREFIX));
  }

  @Test
  void absentVerifiedClaimsSerializesNamesOnly() {
    GrantIdTokenClaims claims = new GrantIdTokenClaims(Set.of("name"));

    assertEquals("name", claims.toStringValues());
    assertFalse(claims.hasVerifiedClaims());
  }

  @Test
  void createPersistsVerifiedClaimsAsSentinelNotNameToken() {
    String claimsValue =
        "{\"id_token\":{\"verified_claims\":{\"verification\":{\"trust_framework\":null},"
            + "\"claims\":{\"given_name\":null}}}}";
    RequestedIdTokenClaims idToken = JSON.read(claimsValue, RequestedClaimsPayload.class).idToken();

    GrantIdTokenClaims result =
        GrantIdTokenClaims.create(
            new Scopes(Set.of("openid")), ResponseType.code, List.of(), idToken, false);

    assertTrue(result.hasVerifiedClaims(), "create() must persist the requested verified_claims");
    assertTrue(
        result.toStringValues().contains(RequestedVerifiedClaims.SENTINEL_PREFIX),
        "verified_claims must be persisted as a sentinel");
    assertFalse(
        result.toStringSet().contains("verified_claims"),
        "the bare verified_claims name token must no longer be written");
  }

  @Test
  void mergeCarriesVerifiedClaimsFromEitherSide() {
    GrantIdTokenClaims withVc =
        new GrantIdTokenClaims(
            Set.of("email"), RequestedVerifiedClaims.of(sampleVerifiedClaimsRequest()));
    GrantIdTokenClaims namesOnly = new GrantIdTokenClaims(Set.of("name"));

    // existing has it, incoming does not -> preserved
    assertTrue(namesOnly.merge(withVc).hasVerifiedClaims());
    // incoming has it, existing does not -> preserved
    assertTrue(withVc.merge(namesOnly).hasVerifiedClaims());
    // names are unioned regardless
    assertEquals(Set.of("email", "name"), withVc.merge(namesOnly).toStringSet());
  }
}
