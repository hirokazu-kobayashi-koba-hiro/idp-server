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

    User updated = IdentityVerificationUserUpdater.update(user, context(Map.of()), resultConfig);

    assertEquals(UserStatus.IDENTITY_VERIFIED, updated.status());
  }

  @Test
  void testConfiguredUserStatusIsApplied() {
    User user = registeredUser().setStatus(UserStatus.INITIALIZED);
    IdentityVerificationResultConfig resultConfig =
        config(Map.of("user_status", "IDENTITY_VERIFICATION_REQUIRED"));

    User updated = IdentityVerificationUserUpdater.update(user, context(Map.of()), resultConfig);

    assertEquals(UserStatus.IDENTITY_VERIFICATION_REQUIRED, updated.status());
  }

  @Test
  void testKeepUserStatusDoesNotTransit() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "KEEP"));

    User updated = IdentityVerificationUserUpdater.update(user, context(Map.of()), resultConfig);

    assertEquals(UserStatus.REGISTERED, updated.status());
  }

  @Test
  void testSameUserStatusIsNoOp() {
    User user = registeredUser();
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "REGISTERED"));

    User updated = IdentityVerificationUserUpdater.update(user, context(Map.of()), resultConfig);

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
            user, context(Map.of("last_name", "Yamada", "first_name", "Hanako")), resultConfig);

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
            user, context(Map.of("kyc_level", "gold")), resultConfig);

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
        () -> IdentityVerificationUserUpdater.update(user, context(Map.of()), resultConfig));
  }

  @Test
  void testDisallowedLifecycleTransitionFailsClosed() {
    User user = registeredUser().setStatus(UserStatus.IDENTITY_VERIFIED);
    // IDENTITY_VERIFIED -> REGISTERED は UserLifecycleManager で許可されていない
    IdentityVerificationResultConfig resultConfig = config(Map.of("user_status", "REGISTERED"));

    assertThrows(
        UnSupportedException.class,
        () -> IdentityVerificationUserUpdater.update(user, context(Map.of()), resultConfig));
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
            user, context(Map.of("first_name", "Hanako")), resultConfig);

    // マッピング元が存在しない項目は既存値を保持し、存在する項目だけ更新される
    assertEquals("Original", updated.familyName());
    assertEquals("Hanako", updated.givenName());
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
            user, context(Map.of("last_name", "Yamada", "kyc_level", "gold")), resultConfig);

    assertEquals("Yamada", updated.familyName());
    assertEquals("gold", updated.customPropertiesValue().get("kyc_level"));
    assertEquals(UserStatus.IDENTITY_VERIFIED, updated.status());
  }
}
