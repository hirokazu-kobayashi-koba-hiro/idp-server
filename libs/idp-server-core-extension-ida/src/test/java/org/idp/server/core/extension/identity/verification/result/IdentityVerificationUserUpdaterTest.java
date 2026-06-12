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

package org.idp.server.core.extension.identity.verification.result;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContextBuilder;
import org.idp.server.core.extension.identity.verification.configuration.verified_claims.IdentityVerificationResultConfig;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class IdentityVerificationUserUpdaterTest {

  private User registeredUser() {
    HashMap<String, Object> customProperties = new HashMap<>();
    customProperties.put("existing_key", "existing_value");
    return new User()
        .setSub("user-001")
        .setName("old name")
        .setStatus(UserStatus.REGISTERED)
        .setCustomProperties(customProperties);
  }

  private IdentityVerificationContext context(Map<String, Object> requestBody) {
    return new IdentityVerificationContextBuilder()
        .request(new IdentityVerificationRequest(requestBody))
        .build();
  }

  private IdentityVerificationResultConfig config(Map<String, Object> map) {
    return JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);
  }

  @Test
  void testDefaultTransitsToIdentityVerified() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig = config(Map.of());

    User updated =
        IdentityVerificationUserUpdater.update(user, context(Map.of()), Map.of(), resultConfig);

    assertEquals(UserStatus.IDENTITY_VERIFIED, updated.status());
  }

  @Test
  void testConfiguredUserStatusIsApplied() {
    User user = registeredUser().setStatus(UserStatus.INITIALIZED);
    IdentityVerificationResultConfig resultConfig =
        config(Map.of("user_status", "IDENTITY_VERIFICATION_REQUIRED"));

    User updated =
        IdentityVerificationUserUpdater.update(user, context(Map.of()), Map.of(), resultConfig);

    assertEquals(UserStatus.IDENTITY_VERIFICATION_REQUIRED, updated.status());
  }

  @Test
  void testKeepUserStatusDoesNotTransit() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(user, context(Map.of()), Map.of(), resultConfig);

    assertEquals(UserStatus.REGISTERED, updated.status());
  }

  @Test
  void testSameUserStatusIsNoOp() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "REGISTERED"));

    User updated =
        IdentityVerificationUserUpdater.update(user, context(Map.of()), Map.of(), resultConfig);

    assertEquals(UserStatus.REGISTERED, updated.status());
  }

  @Test
  void testUserClaimsMappingPatchesStandardClaims() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "user_claims_mapping_rules",
                List.of(
                    Map.of("from", "$.request_body.last_name", "to", "family_name"),
                    Map.of("from", "$.request_body.first_name", "to", "given_name"))));

    User updated =
        IdentityVerificationUserUpdater.update(
            user,
            context(Map.of("last_name", "Yamada", "first_name", "Hanako")),
            Map.of(),
            resultConfig);

    assertEquals("Yamada", updated.familyName());
    assertEquals("Hanako", updated.givenName());
    assertEquals("old name", updated.name());
    assertEquals("user-001", updated.sub());
  }

  @Test
  void testCustomPropertiesMappingMergesWithExistingProperties() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "custom_properties_mapping_rules",
                List.of(Map.of("from", "$.request_body.kyc_level", "to", "kyc_level")),
                "user_status",
                "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(
            user, context(Map.of("kyc_level", "gold")), Map.of(), resultConfig);

    assertEquals("gold", updated.customPropertiesValue().get("kyc_level"));
    assertEquals("existing_value", updated.customPropertiesValue().get("existing_key"));
  }

  @Test
  void testUserClaimsMappingCannotChangeStatusAndCustomProperties() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "user_claims_mapping_rules",
                List.of(
                    Map.of("from", "$.request_body.status", "to", "status"),
                    Map.of("from", "$.request_body.props", "to", "custom_properties")),
                "user_status",
                "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(
            user,
            context(Map.of("status", "DELETED", "props", Map.of("injected", true))),
            Map.of(),
            resultConfig);

    assertEquals(UserStatus.REGISTERED, updated.status());
    assertFalse(updated.customPropertiesValue().containsKey("injected"));
    assertEquals("existing_value", updated.customPropertiesValue().get("existing_key"));
  }

  @Test
  void testInvalidUserStatusFailsClosed() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "TYPO_STATUS"));

    // 不正な設定値は UNKNOWN になり、ライフサイクル遷移で拒否される（fail-closed）
    assertThrows(
        UnSupportedException.class,
        () ->
            IdentityVerificationUserUpdater.update(
                user, context(Map.of()), Map.of(), resultConfig));
  }

  @Test
  void testDisallowedLifecycleTransitionFailsClosed() {
    User user = registeredUser().setStatus(UserStatus.IDENTITY_VERIFIED);
    // IDENTITY_VERIFIED -> REGISTERED は UserLifecycleManager で許可されていない
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "REGISTERED"));

    assertThrows(
        UnSupportedException.class,
        () ->
            IdentityVerificationUserUpdater.update(
                user, context(Map.of()), Map.of(), resultConfig));
  }

  @Test
  void testMissingSourcePathKeepsExistingValues() {
    User user = registeredUser().setFamilyName("Original");
    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "user_claims_mapping_rules",
                List.of(
                    Map.of("from", "$.request_body.nonexistent_field", "to", "family_name"),
                    Map.of("from", "$.request_body.first_name", "to", "given_name")),
                "user_status",
                "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(
            user, context(Map.of("first_name", "Hanako")), Map.of(), resultConfig);

    // マッピング元が存在しない項目は既存値を保持し、存在する項目だけ更新される
    assertEquals("Original", updated.familyName());
    assertEquals("Hanako", updated.givenName());
  }

  @Test
  void testVerifiedClaimsDefaultMergeReplacesTopLevelObjects() {
    HashMap<String, Object> existing = new HashMap<>();
    existing.put("claims", Map.of("family_name", "Yamada", "address", Map.of("country", "JP")));
    existing.put("verification", Map.of("trust_framework", "eidas"));
    User user = registeredUser().setVerifiedClaims(existing);

    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(
            user,
            context(Map.of()),
            Map.of("claims", Map.of("annual_income", 8000000)),
            resultConfig);

    // デフォルト merge はトップレベル putAll のため claims オブジェクトは丸ごと差し替わる
    Map<String, Object> claims = (Map<String, Object>) updated.verifiedClaims().get("claims");
    assertEquals(8000000, claims.get("annual_income"));
    assertFalse(claims.containsKey("family_name"));
    // 出力されなかったトップレベルキーは保持される
    assertTrue(updated.verifiedClaims().containsKey("verification"));
  }

  @Test
  void testVerifiedClaimsDeepMergePreservesExistingClaims() {
    HashMap<String, Object> existing = new HashMap<>();
    existing.put("claims", Map.of("family_name", "Yamada", "address", Map.of("country", "JP")));
    existing.put("verification", Map.of("trust_framework", "eidas"));
    User user = registeredUser().setVerifiedClaims(existing);

    IdentityVerificationResultConfig resultConfig =
        config(Map.of("verified_claims_update_policy", "deep_merge", "user_status", "KEEP"));

    Map<String, Object> produced = new HashMap<>();
    Map<String, Object> producedClaims = new HashMap<>();
    producedClaims.put("annual_income", 8000000);
    producedClaims.put("occupation", null);
    produced.put("claims", producedClaims);

    User updated =
        IdentityVerificationUserUpdater.update(user, context(Map.of()), produced, resultConfig);

    Map<String, Object> claims = (Map<String, Object>) updated.verifiedClaims().get("claims");
    // 既存クレームを保持しつつ新クレームが追加される
    assertEquals("Yamada", claims.get("family_name"));
    assertEquals(Map.of("country", "JP"), claims.get("address"));
    assertEquals(8000000, claims.get("annual_income"));
    // null 値のキーは無視される
    assertFalse(claims.containsKey("occupation"));
    // verification は出力されていないので保持
    assertEquals(Map.of("trust_framework", "eidas"), updated.verifiedClaims().get("verification"));
  }

  @Test
  void testVerifiedClaimsReplacePolicy() {
    HashMap<String, Object> existing = new HashMap<>();
    existing.put("claims", Map.of("family_name", "Yamada"));
    existing.put("verification", Map.of("trust_framework", "eidas"));
    User user = registeredUser().setVerifiedClaims(existing);

    IdentityVerificationResultConfig resultConfig =
        config(Map.of("verified_claims_update_policy", "replace", "user_status", "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(
            user,
            context(Map.of()),
            Map.of("claims", Map.of("annual_income", 8000000)),
            resultConfig);

    // 完全置換: 出力されなかった verification も消える
    assertFalse(updated.verifiedClaims().containsKey("verification"));
    Map<String, Object> claims = (Map<String, Object>) updated.verifiedClaims().get("claims");
    assertEquals(8000000, claims.get("annual_income"));
    assertFalse(claims.containsKey("family_name"));
  }

  @Test
  void testCustomPropertiesMergeKeepsExistingValueWhenSourceMissing() {
    User user = registeredUser();
    user.customPropertiesValue().put("kyc_level", "gold");

    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "custom_properties_mapping_rules",
                List.of(Map.of("from", "$.request_body.nonexistent", "to", "kyc_level")),
                "user_status",
                "KEEP"));

    User updated =
        IdentityVerificationUserUpdater.update(user, context(Map.of()), Map.of(), resultConfig);

    // merge: ソース欠落キーは null 上書きせず既存値を保持する
    assertEquals("gold", updated.customPropertiesValue().get("kyc_level"));
  }

  @Test
  void testCustomPropertiesReplaceManagedSynchronizesDeclaredKeys() {
    User user = registeredUser();
    user.customPropertiesValue().put("kyc_level", "gold");
    user.customPropertiesValue().put("risk_flag", "high");

    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "custom_properties_mapping_rules",
                List.of(
                    Map.of("from", "$.request_body.kyc_level", "to", "kyc_level"),
                    Map.of("from", "$.request_body.risk_flag", "to", "risk_flag")),
                "custom_properties_update_policy",
                "replace_managed",
                "user_status",
                "KEEP"));

    // 今回の審査では risk_flag に該当なし（リクエストに含まれない）
    User updated =
        IdentityVerificationUserUpdater.update(
            user, context(Map.of("kyc_level", "platinum")), Map.of(), resultConfig);

    // 宣言キーは審査結果と同期: 値が出たキーは更新、出なかったキーは削除
    assertEquals("platinum", updated.customPropertiesValue().get("kyc_level"));
    assertFalse(updated.customPropertiesValue().containsKey("risk_flag"));
    // 宣言外のキー（他type・Federation由来）は不可侵
    assertEquals("existing_value", updated.customPropertiesValue().get("existing_key"));
  }

  @Test
  void testAllUpdatesCombined() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig =
        config(
            Map.of(
                "user_claims_mapping_rules",
                List.of(Map.of("from", "$.request_body.last_name", "to", "family_name")),
                "custom_properties_mapping_rules",
                List.of(Map.of("from", "$.request_body.kyc_level", "to", "kyc_level"))));

    User updated =
        IdentityVerificationUserUpdater.update(
            user,
            context(Map.of("last_name", "Yamada", "kyc_level", "gold")),
            Map.of(),
            resultConfig);

    assertEquals("Yamada", updated.familyName());
    assertEquals("gold", updated.customPropertiesValue().get("kyc_level"));
    assertEquals(UserStatus.IDENTITY_VERIFIED, updated.status());
  }
}
