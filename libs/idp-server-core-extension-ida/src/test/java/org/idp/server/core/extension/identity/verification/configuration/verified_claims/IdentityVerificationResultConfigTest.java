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

package org.idp.server.core.extension.identity.verification.configuration.verified_claims;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class IdentityVerificationResultConfigTest {

  @Test
  void testDefaultsKeepBackwardCompatibility() {
    IdentityVerificationResultConfig config = new IdentityVerificationResultConfig();

    assertFalse(config.hasUserClaimsMappingRules());
    assertFalse(config.hasCustomPropertiesMappingRules());
    assertFalse(config.hasUserStatus());
    assertTrue(config.requiresUserStatusTransition());
    assertEquals(UserStatus.IDENTITY_VERIFIED, config.userStatus());
  }

  @Test
  void testDeserializationWithNewFields() {
    Map<String, Object> map =
        Map.of(
            "verified_claims_mapping_rules",
                List.of(
                    Map.of(
                        "from",
                        "$.application.application_details.last_name",
                        "to",
                        "claims.family_name")),
            "user_claims_mapping_rules",
                List.of(
                    Map.of(
                        "from",
                        "$.application.application_details.last_name",
                        "to",
                        "family_name")),
            "custom_properties_mapping_rules",
                List.of(
                    Map.of(
                        "from", "$.application.application_details.kyc_level", "to", "kyc_level")),
            "user_status", "REGISTERED");

    IdentityVerificationResultConfig config =
        JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);

    assertTrue(config.hasUserClaimsMappingRules());
    assertEquals("family_name", config.userClaimsMappingRules().get(0).to());
    assertTrue(config.hasCustomPropertiesMappingRules());
    assertEquals("kyc_level", config.customPropertiesMappingRules().get(0).to());
    assertTrue(config.hasUserStatus());
    assertTrue(config.requiresUserStatusTransition());
    assertEquals(UserStatus.REGISTERED, config.userStatus());
  }

  @Test
  void testUserStatusKeepDisablesTransition() {
    Map<String, Object> map = Map.of("user_status", "KEEP");

    IdentityVerificationResultConfig config =
        JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);

    assertTrue(config.hasUserStatus());
    assertFalse(config.requiresUserStatusTransition());
  }

  @Test
  void testUserStatusKeepIsCaseInsensitive() {
    Map<String, Object> map = Map.of("user_status", "keep");

    IdentityVerificationResultConfig config =
        JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);

    assertFalse(config.requiresUserStatusTransition());
  }

  @Test
  void testToMapContainsNewFields() {
    Map<String, Object> map =
        Map.of(
            "user_claims_mapping_rules",
                List.of(
                    Map.of(
                        "from",
                        "$.application.application_details.last_name",
                        "to",
                        "family_name")),
            "custom_properties_mapping_rules",
                List.of(
                    Map.of(
                        "from", "$.application.application_details.kyc_level", "to", "kyc_level")),
            "user_status", "KEEP");

    IdentityVerificationResultConfig config =
        JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);
    Map<String, Object> result = config.toMap();

    assertTrue(result.containsKey("verified_claims_mapping_rules"));
    assertTrue(result.containsKey("source_details_mapping_rules"));
    assertTrue(result.containsKey("user_claims_mapping_rules"));
    assertTrue(result.containsKey("custom_properties_mapping_rules"));
    assertEquals("KEEP", result.get("user_status"));
  }

  @Test
  void testToMapOmitsUserStatusWhenUnset() {
    IdentityVerificationResultConfig config = new IdentityVerificationResultConfig();

    Map<String, Object> result = config.toMap();

    assertFalse(result.containsKey("user_status"));
  }

  @Test
  void testUpdatePolicyDefaultsToMerge() {
    IdentityVerificationResultConfig config = new IdentityVerificationResultConfig();

    assertFalse(config.isVerifiedClaimsDeepMerge());
    assertFalse(config.isVerifiedClaimsReplace());
    assertFalse(config.isCustomPropertiesReplaceManaged());
    assertFalse(config.toMap().containsKey("verified_claims_update_policy"));
    assertFalse(config.toMap().containsKey("custom_properties_update_policy"));
  }

  @Test
  void testUpdatePolicyDeserialization() {
    Map<String, Object> map =
        Map.of(
            "verified_claims_update_policy", "deep_merge",
            "custom_properties_update_policy", "replace_managed");

    IdentityVerificationResultConfig config =
        JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);

    assertTrue(config.isVerifiedClaimsDeepMerge());
    assertFalse(config.isVerifiedClaimsReplace());
    assertTrue(config.isCustomPropertiesReplaceManaged());
    assertEquals("deep_merge", config.toMap().get("verified_claims_update_policy"));
    assertEquals("replace_managed", config.toMap().get("custom_properties_update_policy"));
  }

  @Test
  void testCustomPropertiesManagedKeys() {
    Map<String, Object> map =
        Map.of(
            "custom_properties_mapping_rules",
            List.of(
                Map.of("from", "$.request_body.kyc_level", "to", "kyc_level"),
                Map.of("from", "$.request_body.risk", "to", "risk.flag"),
                Map.of("from", "$.request_body", "to", "*")));

    IdentityVerificationResultConfig config =
        JsonConverter.snakeCaseInstance().read(map, IdentityVerificationResultConfig.class);

    // ネストの to はトップレベルキー、"*" は対象外
    assertEquals(java.util.Set.of("kyc_level", "risk"), config.customPropertiesManagedKeys());
  }
}
