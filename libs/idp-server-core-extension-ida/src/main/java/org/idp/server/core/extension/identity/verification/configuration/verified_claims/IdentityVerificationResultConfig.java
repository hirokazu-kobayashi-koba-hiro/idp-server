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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class IdentityVerificationResultConfig implements JsonReadable {

  static final String USER_STATUS_KEEP = "KEEP";
  static final String VERIFIED_CLAIMS_POLICY_DEEP_MERGE = "deep_merge";
  static final String VERIFIED_CLAIMS_POLICY_REPLACE = "replace";
  static final String CUSTOM_PROPERTIES_POLICY_REPLACE_MANAGED = "replace_managed";

  List<MappingRule> verifiedClaimsMappingRules = new ArrayList<>();
  List<MappingRule> sourceDetailsMappingRules = new ArrayList<>();
  List<MappingRule> userClaimsMappingRules = new ArrayList<>();
  List<MappingRule> customPropertiesMappingRules = new ArrayList<>();
  String userStatus;
  String verifiedClaimsUpdatePolicy;
  String customPropertiesUpdatePolicy;

  public IdentityVerificationResultConfig() {}

  public IdentityVerificationResultConfig(
      List<MappingRule> verifiedClaimsMappingRules, List<MappingRule> sourceDetailsMappingRules) {
    this.verifiedClaimsMappingRules = verifiedClaimsMappingRules;
    this.sourceDetailsMappingRules = sourceDetailsMappingRules;
  }

  public List<MappingRule> verifiedClaimsMappingRules() {
    if (verifiedClaimsMappingRules == null) {
      return new ArrayList<>();
    }
    return verifiedClaimsMappingRules;
  }

  public List<Map<String, Object>> verifiedClaimsMappingRulesAsMap() {
    if (verifiedClaimsMappingRules == null) {
      return new ArrayList<>();
    }
    return verifiedClaimsMappingRules.stream().map(MappingRule::toMap).collect(Collectors.toList());
  }

  public List<MappingRule> sourceDetailsMappingRules() {
    if (sourceDetailsMappingRules == null) {
      return new ArrayList<>();
    }
    return sourceDetailsMappingRules;
  }

  public List<Map<String, Object>> sourceDetailsMappingRulesAsMap() {
    if (sourceDetailsMappingRules == null) {
      return new ArrayList<>();
    }
    return sourceDetailsMappingRules.stream().map(MappingRule::toMap).collect(Collectors.toList());
  }

  public List<MappingRule> userClaimsMappingRules() {
    if (userClaimsMappingRules == null) {
      return new ArrayList<>();
    }
    return userClaimsMappingRules;
  }

  public List<Map<String, Object>> userClaimsMappingRulesAsMap() {
    return userClaimsMappingRules().stream().map(MappingRule::toMap).collect(Collectors.toList());
  }

  public boolean hasUserClaimsMappingRules() {
    return userClaimsMappingRules != null && !userClaimsMappingRules.isEmpty();
  }

  public List<MappingRule> customPropertiesMappingRules() {
    if (customPropertiesMappingRules == null) {
      return new ArrayList<>();
    }
    return customPropertiesMappingRules;
  }

  public List<Map<String, Object>> customPropertiesMappingRulesAsMap() {
    return customPropertiesMappingRules().stream()
        .map(MappingRule::toMap)
        .collect(Collectors.toList());
  }

  public boolean hasCustomPropertiesMappingRules() {
    return customPropertiesMappingRules != null && !customPropertiesMappingRules.isEmpty();
  }

  public boolean hasUserStatus() {
    return userStatus != null && !userStatus.isEmpty();
  }

  public boolean requiresUserStatusTransition() {
    return !hasUserStatus() || !USER_STATUS_KEEP.equalsIgnoreCase(userStatus);
  }

  public UserStatus userStatus() {
    if (!hasUserStatus()) {
      return UserStatus.IDENTITY_VERIFIED;
    }
    return UserStatus.of(userStatus);
  }

  /** Raw configured user_status string (for diagnostics/logging); may be null or invalid. */
  public String userStatusValue() {
    return userStatus;
  }

  public boolean hasVerifiedClaimsUpdatePolicy() {
    return verifiedClaimsUpdatePolicy != null && !verifiedClaimsUpdatePolicy.isEmpty();
  }

  public boolean isVerifiedClaimsDeepMerge() {
    return VERIFIED_CLAIMS_POLICY_DEEP_MERGE.equalsIgnoreCase(verifiedClaimsUpdatePolicy);
  }

  public boolean isVerifiedClaimsReplace() {
    return VERIFIED_CLAIMS_POLICY_REPLACE.equalsIgnoreCase(verifiedClaimsUpdatePolicy);
  }

  public boolean hasCustomPropertiesUpdatePolicy() {
    return customPropertiesUpdatePolicy != null && !customPropertiesUpdatePolicy.isEmpty();
  }

  public boolean isCustomPropertiesReplaceManaged() {
    return CUSTOM_PROPERTIES_POLICY_REPLACE_MANAGED.equalsIgnoreCase(customPropertiesUpdatePolicy);
  }

  /**
   * Top-level keys declared as "to" targets of custom_properties_mapping_rules. Used by
   * replace_managed to synchronize only the keys this configuration owns.
   */
  public Set<String> customPropertiesManagedKeys() {
    Set<String> keys = new LinkedHashSet<>();
    for (MappingRule rule : customPropertiesMappingRules()) {
      String to = rule.to();
      if (to == null || to.isEmpty() || "*".equals(to)) {
        continue;
      }
      keys.add(to.split("\\.")[0]);
    }
    return keys;
  }

  public boolean exists() {
    return verifiedClaimsMappingRules != null && !verifiedClaimsMappingRules.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("verified_claims_mapping_rules", verifiedClaimsMappingRulesAsMap());
    map.put("source_details_mapping_rules", sourceDetailsMappingRulesAsMap());
    map.put("user_claims_mapping_rules", userClaimsMappingRulesAsMap());
    map.put("custom_properties_mapping_rules", customPropertiesMappingRulesAsMap());
    if (hasUserStatus()) map.put("user_status", userStatus);
    if (hasVerifiedClaimsUpdatePolicy())
      map.put("verified_claims_update_policy", verifiedClaimsUpdatePolicy);
    if (hasCustomPropertiesUpdatePolicy())
      map.put("custom_properties_update_policy", customPropertiesUpdatePolicy);
    return map;
  }
}
