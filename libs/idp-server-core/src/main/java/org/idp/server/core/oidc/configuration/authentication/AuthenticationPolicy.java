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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationPolicy implements JsonReadable {
  String id;
  int priority;
  AuthenticationPolicyCondition conditions;
  List<String> availableMethods;
  Map<String, List<String>> acrMapper;
  AuthenticationResultConditions successConditions;
  AuthenticationResultConditions failureConditions;
  AuthenticationResultConditions lockConditions;

  public AuthenticationPolicy() {}

  public boolean anyMatch(AuthorizationFlow authorizationFlow, AcrValues acrValues, Scopes scopes) {
    return conditions.anyMatch(authorizationFlow, acrValues, scopes);
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

  public AcrMapper acrMapper() {
    return new AcrMapper(acrMapper);
  }

  public boolean hasAcrMapper() {
    return acrMapper != null;
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

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    if (hasPolicyConditions()) map.put("conditions", conditions.toMap());
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    if (hasAcrMapper()) map.put("acr_mapper", acrMapper);
    if (hasSuccessConditions()) map.put("success_conditions", successConditions.toMap());
    if (hasFailureConditions()) map.put("failure_conditions", failureConditions.toMap());
    if (hasLockConditions()) map.put("lock_conditions", lockConditions.toMap());
    return map;
  }
}
