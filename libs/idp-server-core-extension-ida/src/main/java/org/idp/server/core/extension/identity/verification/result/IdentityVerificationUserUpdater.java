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
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class IdentityVerificationUserUpdater {

  public static User update(
      User user,
      IdentityVerificationContext context,
      Map<String, Object> verifiedClaims,
      IdentityVerificationResultConfig resultConfig) {

    User updated = user;

    if (resultConfig.hasUserClaimsMappingRules()) {
      Map<String, Object> mapped = execute(context, resultConfig.userClaimsMappingRules());
      // status and custom_properties have dedicated configurations
      // (user_status, custom_properties_mapping_rules); status must go through
      // UserLifecycleManager and custom properties are merged, not replaced
      mapped.remove("status");
      mapped.remove("custom_properties");
      User patchUser = JsonConverter.snakeCaseInstance().read(mapped, User.class);
      updated = updated.updateWith(patchUser);
    }

    updated = applyVerifiedClaims(updated, verifiedClaims, resultConfig);
    updated = applyCustomProperties(updated, context, resultConfig);

    if (resultConfig.requiresUserStatusTransition()) {
      UserStatus newStatus = resultConfig.userStatus();
      if (updated.status() != newStatus) {
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

    if (resultConfig.isVerifiedClaimsDeepMerge()) {
      Map<String, Object> merged =
          user.hasVerifiedClaims() ? new HashMap<>(user.verifiedClaims()) : new HashMap<>();
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

    return user.mergeVerifiedClaims(verifiedClaims);
  }

  static User applyCustomProperties(
      User user,
      IdentityVerificationContext context,
      IdentityVerificationResultConfig resultConfig) {

    if (!resultConfig.hasCustomPropertiesMappingRules()) {
      return user;
    }

    Map<String, Object> mapped = execute(context, resultConfig.customPropertiesMappingRules());

    if (resultConfig.isCustomPropertiesReplaceManaged()) {
      Set<String> managedKeys = resultConfig.customPropertiesManagedKeys();
      HashMap<String, Object> next = new HashMap<>(user.customPropertiesValue());
      managedKeys.forEach(next::remove);
      mapped.forEach(
          (key, value) -> {
            if (value != null) next.put(key, value);
          });
      return user.setCustomProperties(next);
    }

    // merge: keys without a produced value keep their existing value
    HashMap<String, Object> nonNullValues = new HashMap<>();
    mapped.forEach(
        (key, value) -> {
          if (value != null) nonNullValues.put(key, value);
        });
    return user.addCustomProperties(nonNullValues);
  }

  static Map<String, Object> execute(
      IdentityVerificationContext context, List<MappingRule> mappingRules) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(context.toMap());
    JsonPathWrapper jsonPath = new JsonPathWrapper(jsonNodeWrapper.toJson());
    return MappingRuleObjectMapper.execute(mappingRules, jsonPath);
  }
}
