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
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class IdentityVerificationVerifiedClaimsConfiguration implements JsonReadable {

  List<MappingRule> mappingRules = new ArrayList<>();

  public IdentityVerificationVerifiedClaimsConfiguration() {}

  public IdentityVerificationVerifiedClaimsConfiguration(List<MappingRule> mappingRules) {
    this.mappingRules = mappingRules;
  }

  public List<MappingRule> mappingRules() {
    if (mappingRules == null) {
      return new ArrayList<>();
    }
    return mappingRules;
  }

  public boolean exists() {
    return mappingRules != null && !mappingRules.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("mapping_rules", mappingRules);
    return map;
  }
}
