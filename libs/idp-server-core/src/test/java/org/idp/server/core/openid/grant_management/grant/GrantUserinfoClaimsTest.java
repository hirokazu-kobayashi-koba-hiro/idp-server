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
import org.idp.server.core.openid.identity.id_token.RequestedUserinfoClaims;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression for #1594.
 *
 * <p>各 {@code shouldAddXxx} は自分自身のクレームの {@code hasXxx()} を参照しなければならない。コピペで別クレームの has()
 * を参照していると、{@code claims} リクエストパラメータ経由の個別要求で「要求したクレームが出ない / 別クレーム要求で 誤混入する」という不具合になる。
 *
 * <p>表駆動で「クレーム X を単独要求 → 出力が {X} だけ」を全標準クレームについて検証することで、対応のズレを構造的に検出する。
 */
class GrantUserinfoClaimsTest {

  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();

  /** UserInfo で scope/claims から出し分けされる標準クレーム一覧。 */
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

  private static RequestedUserinfoClaims requestOnly(String claim) {
    return JSON.read("{\"" + claim + "\":{\"essential\":true}}", RequestedUserinfoClaims.class);
  }

  @ParameterizedTest(name = "request only [{0}] -> grant userinfo claims == [{0}]")
  @MethodSource("standardClaims")
  void claimsParameterMapsEachClaimToItself(String claim) {
    GrantUserinfoClaims result =
        GrantUserinfoClaims.create(new Scopes(), STANDARD_CLAIMS, requestOnly(claim));

    assertEquals(
        Set.of(claim),
        result.toStringSet(),
        "requesting only '" + claim + "' must yield exactly that claim");
  }

  @Test
  void profileScopeIncludesProfileFamilyClaims() {
    GrantUserinfoClaims result =
        GrantUserinfoClaims.create(
            new Scopes(Set.of("profile")), STANDARD_CLAIMS, new RequestedUserinfoClaims());

    assertTrue(result.contains("name"));
    assertTrue(result.contains("given_name"));
    assertTrue(result.contains("family_name"));
    assertTrue(result.contains("middle_name"));
    assertFalse(result.contains("email"));
    assertFalse(result.contains("phone_number"));
    assertFalse(result.contains("address"));
  }

  @Test
  void emailScopeIncludesEmailClaims() {
    GrantUserinfoClaims result =
        GrantUserinfoClaims.create(
            new Scopes(Set.of("email")), STANDARD_CLAIMS, new RequestedUserinfoClaims());

    assertTrue(result.contains("email"));
    assertTrue(result.contains("email_verified"));
    assertFalse(result.contains("name"));
  }

  @Test
  void requestedClaimNotInSupportedIsExcluded() {
    List<String> supportedWithoutFamilyName =
        STANDARD_CLAIMS.stream().filter(c -> !c.equals("family_name")).toList();

    GrantUserinfoClaims result =
        GrantUserinfoClaims.create(
            new Scopes(), supportedWithoutFamilyName, requestOnly("family_name"));

    assertFalse(result.contains("family_name"));
  }
}
