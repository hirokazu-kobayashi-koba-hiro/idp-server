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

package org.idp.server.core.openid.authentication.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationPolicyCondition implements JsonReadable {
  List<String> authorizationFlows = new ArrayList<>();
  List<String> acrValues = new ArrayList<>();
  List<String> scopes = new ArrayList<>();

  public AuthenticationPolicyCondition() {}

  public AuthenticationPolicyCondition(
      List<String> authorizationFlows, List<String> acrValues, List<String> scopes) {
    this.authorizationFlows = authorizationFlows;
    this.acrValues = acrValues;
    this.scopes = scopes;
  }

  public boolean anyMatch(AuthFlow authFlow, AcrValues acrValues, Scopes scopes) {
    if (authorizationFlows.contains(authFlow.name())) {
      return true;
    }

    if (this.acrValues.stream().anyMatch(acrValues::contains)) {
      return true;
    }

    return this.scopes.stream().anyMatch(scopes::contains);
  }

  public List<String> authorizationFlows() {
    return authorizationFlows;
  }

  public List<String> acrValues() {
    return acrValues;
  }

  public List<String> scopes() {
    return scopes;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("authorization_flows", authorizationFlows);
    map.put("acr_values", acrValues);
    map.put("scopes", scopes);
    return map;
  }
}
