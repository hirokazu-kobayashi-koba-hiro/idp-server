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
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfig;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationPreHookConfig implements JsonReadable {
  List<IdentityVerificationConfig> verifications = new ArrayList<>();
  List<IdentityVerificationConfig> additionalParameters = new ArrayList<>();

  public IdentityVerificationPreHookConfig() {}

  public List<IdentityVerificationConfig> verifications() {
    if (verifications == null) {
      return new ArrayList<>();
    }
    return verifications;
  }

  public List<Map<String, Object>> verificationsAsMap() {
    if (verifications == null) {
      return new ArrayList<>();
    }
    return verifications.stream().map(IdentityVerificationConfig::toMap).toList();
  }

  public boolean hasVerifications() {
    return verifications != null && !verifications.isEmpty();
  }

  public List<IdentityVerificationConfig> additionalParameters() {
    if (additionalParameters == null) {
      return new ArrayList<>();
    }
    return additionalParameters;
  }

  public List<Map<String, Object>> additionalParametersAsMap() {
    if (additionalParameters == null) {
      return new ArrayList<>();
    }
    return additionalParameters.stream().map(IdentityVerificationConfig::toMap).toList();
  }

  public boolean hasAdditionalParameters() {
    return additionalParameters != null && !additionalParameters.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasVerifications()) map.put("verifications", verificationsAsMap());
    if (hasAdditionalParameters()) map.put("additional_parameters", additionalParametersAsMap());
    return map;
  }
}
