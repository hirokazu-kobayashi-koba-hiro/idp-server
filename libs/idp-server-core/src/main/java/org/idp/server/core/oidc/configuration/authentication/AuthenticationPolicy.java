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

package org.idp.server.core.oidc.configuration.authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.AuthFlow;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationPolicy implements JsonReadable {
  String id;
  int priority;
  AuthenticationPolicyCondition conditions = new AuthenticationPolicyCondition();
  List<String> availableMethods = new ArrayList<>();
  Map<String, List<String>> acrMappingRules = new HashMap<>();
  Map<String, List<String>> levelOfAuthenticationScopes = new HashMap<>();
  AuthenticationResultConditions successConditions = new AuthenticationResultConditions();
  AuthenticationResultConditions failureConditions = new AuthenticationResultConditions();
  AuthenticationResultConditions lockConditions = new AuthenticationResultConditions();
  AuthenticationDeviceRule authenticationDeviceRule = new AuthenticationDeviceRule();

  public AuthenticationPolicy() {}

  public boolean anyMatch(AuthFlow authFlow, AcrValues acrValues, Scopes scopes) {
    return conditions.anyMatch(authFlow, acrValues, scopes);
  }

  public AuthenticationPolicyIdentifier identifier() {
    return new AuthenticationPolicyIdentifier(id);
  }

  public int priority() {
    return priority;
  }

  public AuthenticationPolicyCondition conditions() {
    return conditions;
  }

  public boolean hasPolicyConditions() {
    return conditions != null;
  }

  public List<String> availableMethods() {
    return availableMethods;
  }

  public boolean hasAvailableMethods() {
    return availableMethods != null;
  }

  public Map<String, List<String>> acrMappingRules() {
    return acrMappingRules;
  }

  public boolean hasAcrMappingRules() {
    return acrMappingRules != null;
  }

  public Map<String, List<String>> levelOfAuthenticationScopes() {
    return levelOfAuthenticationScopes;
  }

  public boolean hasLevelOfAuthenticationScopes() {
    return levelOfAuthenticationScopes != null;
  }

  public AuthenticationResultConditions successConditions() {
    return successConditions;
  }

  public boolean hasSuccessConditions() {
    return successConditions != null;
  }

  public AuthenticationResultConditions failureConditions() {
    return failureConditions;
  }

  public boolean hasFailureConditions() {
    return failureConditions != null;
  }

  public AuthenticationResultConditions lockConditions() {
    return lockConditions;
  }

  public boolean hasLockConditions() {
    return lockConditions != null;
  }

  public AuthenticationDeviceRule authenticationDeviceRule() {
    return authenticationDeviceRule;
  }

  public boolean hasAuthenticationDeviceRule() {
    return authenticationDeviceRule != null;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    if (hasPolicyConditions()) map.put("conditions", conditions.toMap());
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    if (hasAcrMappingRules()) map.put("acr_mapping_rules", acrMappingRules);
    if (hasLevelOfAuthenticationScopes())
      map.put("level_of_authentication_scopes", levelOfAuthenticationScopes);
    if (hasSuccessConditions()) map.put("success_conditions", successConditions.toMap());
    if (hasFailureConditions()) map.put("failure_conditions", failureConditions.toMap());
    if (hasLockConditions()) map.put("lock_conditions", lockConditions.toMap());
    if (hasAuthenticationDeviceRule())
      map.put("authentication_device_rule", authenticationDeviceRule);
    return map;
  }
}
