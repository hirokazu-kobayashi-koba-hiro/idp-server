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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.core.extension.identity.verification.configuration.verified_claims.IdentityVerificationResultConfig;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationUserUpdater {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationUserUpdater.class);

  /**
   * user_claims_mapping_rules can patch standard OIDC profile claims only. Privilege-related fields
   * (roles, permissions, assigned tenants, authentication devices), identifiers and lifecycle state
   * must never be patchable from verification results, even by tenant configuration.
   * preferred_username is excluded as well: it is the tenant's unique key derived by
   * TenantIdentityPolicy and its uniqueness is enforced only on the registration path.
   */
  static final Set<String> PATCHABLE_STANDARD_CLAIMS =
      Set.of(
          "name",
          "given_name",
          "family_name",
          "middle_name",
          "nickname",
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
          "address");

  /**
   * Applies the result configuration to the user and returns the updated instance.
   *
   * <p>The caller's {@code user} instance is never mutated. Note that the defensive copy below is
   * shallow: collection fields may still be shared with the input until they are re-assigned, so
   * any future modification in this class must build a new map/list and call the corresponding
   * setter — never mutate a collection obtained from the user in place.
   */
  public static User update(
      Tenant tenant,
      User user,
      IdentityVerificationContext context,
      Map<String, Object> verifiedClaims,
      IdentityVerificationResultConfig resultConfig) {

    // defensive copy: the caller's instance is never mutated regardless of configuration
    User updated = user.updateWith(new User());

    if (resultConfig.hasUserClaimsMappingRules()) {
      Map<String, Object> mapped = execute(context, resultConfig.userClaimsMappingRules());
      mapped.keySet().retainAll(PATCHABLE_STANDARD_CLAIMS);
      User patchUser = JsonConverter.snakeCaseInstance().read(mapped, User.class);
      updated = updated.updateWith(patchUser);

      // name/email/phone_number can be sources of the tenant unique key; recalculate
      // preferred_username so it stays consistent with the identity policy (#729 convention)
      if (tenant.identityPolicyConfig() != null) {
        updated = updated.applyIdentityPolicy(tenant.identityPolicyConfig());
      }
    }

    updated = applyVerifiedClaims(updated, verifiedClaims, resultConfig);
    updated = applyCustomProperties(updated, context, resultConfig);

    if (resultConfig.requiresUserStatusTransition()) {
      UserStatus newStatus = resultConfig.userStatus();
      if (newStatus == UserStatus.UNKNOWN) {
        // KEEP fallback: an invalid (typo) user_status is not a known UserStatus and must not fail
        // the entire approval. Keep the current status and surface the misconfiguration in the
        // logs.
        // (A valid-but-disallowed lifecycle transition still fails closed below.)
        log.error(
            "identity verification result: user_status '"
                + resultConfig.userStatusValue()
                + "' is not a known UserStatus. Keeping current status '"
                + updated.status().name()
                + "' (KEEP fallback). Fix the verification result config.");
      } else if (updated.status() != newStatus) {
        updated = updated.transitStatus(newStatus);
      }
    }

    return updated;
  }

  static User applyVerifiedClaims(
      User user,
      Map<String, Object> verifiedClaims,
      IdentityVerificationResultConfig resultConfig) {

    if (resultConfig.isVerifiedClaimsReplace()) {
      return user.setVerifiedClaims(verifiedClaims != null ? verifiedClaims : Map.of());
    }

    if (verifiedClaims == null || verifiedClaims.isEmpty()) {
      return user;
    }

    Map<String, Object> merged =
        user.hasVerifiedClaims() ? new HashMap<>(user.verifiedClaims()) : new HashMap<>();

    if (resultConfig.isVerifiedClaimsDeepMerge()) {
      for (Map.Entry<String, Object> entry : verifiedClaims.entrySet()) {
        Object newValue = entry.getValue();
        Object existingValue = merged.get(entry.getKey());
        if (newValue instanceof Map<?, ?> newChild && existingValue instanceof Map<?, ?> current) {
          Map<String, Object> mergedChild = new HashMap<>();
          current.forEach((k, v) -> mergedChild.put(String.valueOf(k), v));
          newChild.forEach(
              (k, v) -> {
                if (v != null) mergedChild.put(String.valueOf(k), v);
              });
          merged.put(entry.getKey(), mergedChild);
        } else if (newValue != null) {
          merged.put(entry.getKey(), newValue);
        }
      }
      return user.setVerifiedClaims(merged);
    }

    // merge (default): top-level putAll on a copied map
    merged.putAll(verifiedClaims);
    return user.setVerifiedClaims(merged);
  }

  static User applyCustomProperties(
      User user,
      IdentityVerificationContext context,
      IdentityVerificationResultConfig resultConfig) {

    if (!resultConfig.hasCustomPropertiesMappingRules()) {
      return user;
    }

    Map<String, Object> mapped = execute(context, resultConfig.customPropertiesMappingRules());

    HashMap<String, Object> next = new HashMap<>(user.customPropertiesValue());

    if (resultConfig.isCustomPropertiesReplaceManaged()) {
      resultConfig.customPropertiesManagedKeys().forEach(next::remove);
    }

    // keys without a produced value keep their existing value (no null overwrite)
    mapped.forEach(
        (key, value) -> {
          if (value != null) next.put(key, value);
        });
    return user.setCustomProperties(next);
  }

  static Map<String, Object> execute(
      IdentityVerificationContext context, List<MappingRule> mappingRules) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(context.toMap());
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    return MappingRuleObjectMapper.execute(mappingRules, jsonPath);
  }
}
