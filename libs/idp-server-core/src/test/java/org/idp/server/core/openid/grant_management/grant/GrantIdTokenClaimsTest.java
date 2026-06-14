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
import java.util.Set;
import java.util.stream.Stream;
import org.idp.server.core.openid.identity.id_token.RequestedIdTokenClaims;
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
}
