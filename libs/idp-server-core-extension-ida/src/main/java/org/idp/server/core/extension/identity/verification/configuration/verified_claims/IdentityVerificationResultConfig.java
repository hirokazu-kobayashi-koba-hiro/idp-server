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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class IdentityVerificationResultConfig implements JsonReadable {

  List<MappingRule> verifiedClaimsMappingRules = new ArrayList<>();
  List<MappingRule> sourceDetailsMappingRules = new ArrayList<>();

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

  public boolean exists() {
    return verifiedClaimsMappingRules != null && !verifiedClaimsMappingRules.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("verified_claims_mapping_rules", verifiedClaimsMappingRulesAsMap());
    map.put("source_details_mapping_rules", sourceDetailsMappingRulesAsMap());
    return map;
  }
}
