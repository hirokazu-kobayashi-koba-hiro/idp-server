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

package org.idp.server.core.extension.identity.verification.configuration.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class IdentityVerificationStoreConfig implements JsonReadable {

  List<MappingRule> applicationDetailsMappingRules = new ArrayList<>();

  public IdentityVerificationStoreConfig() {}

  public List<MappingRule> applicationDetailsMappingRules() {
    if (applicationDetailsMappingRules == null) {
      return new ArrayList<>();
    }
    return applicationDetailsMappingRules;
  }

  public List<Map<String, Object>> applicationDetailsMappingRulesMap() {
    if (applicationDetailsMappingRules == null) {
      return new ArrayList<>();
    }
    return applicationDetailsMappingRules.stream().map(MappingRule::toMap).toList();
  }

  public boolean hasApplicationDetailsMappingRules() {
    return applicationDetailsMappingRules != null && !applicationDetailsMappingRules.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasApplicationDetailsMappingRules())
      map.put("applicationDetailsMappingRules", applicationDetailsMappingRulesMap());
    return map;
  }
}
