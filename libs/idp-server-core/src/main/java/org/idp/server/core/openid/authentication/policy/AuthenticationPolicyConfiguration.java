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

import java.util.*;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.configuration.Configurable;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuthenticationPolicyConfiguration
    implements JsonReadable, UuidConvertable, Configurable {
  String id;
  String flow;
  List<AuthenticationPolicy> policies;
  boolean enabled = true;

  public AuthenticationPolicyConfiguration() {}

  public AuthenticationPolicyConfiguration(
      String id, String flow, List<AuthenticationPolicy> policies) {
    this.id = id;
    this.flow = flow;
    this.policies = policies;
    this.enabled = true;
  }

  public AuthenticationPolicyConfiguration(
      String id, String flow, List<AuthenticationPolicy> policies, boolean enabled) {
    this.id = id;
    this.flow = flow;
    this.policies = policies;
    this.enabled = enabled;
  }

  public String id() {
    return id;
  }

  public UUID idAsUUID() {
    return convertUuid(id);
  }

  public String flow() {
    return flow;
  }

  public AuthenticationPolicy findSatisfiedAuthenticationPolicy(
      RequestedClientId requestedClientId, AcrValues acrValues, Scopes scopes) {

    if (policies == null || policies.isEmpty()) {
      return new AuthenticationPolicy();
    }

    AuthenticationPolicy filteredPolicy =
        policies.stream()
            .filter(
                authenticationPolicy ->
                    authenticationPolicy.anyMatch(requestedClientId, acrValues, scopes))
            .max(Comparator.comparingInt(AuthenticationPolicy::priority))
            .orElse(new AuthenticationPolicy());

    if (filteredPolicy.exists()) {
      return filteredPolicy;
    }

    return policies.stream().findFirst().orElse(new AuthenticationPolicy());
  }

  public List<Map<String, Object>> policiesAsMap() {
    if (policies == null) {
      return new ArrayList<>();
    }
    return policies.stream().map(AuthenticationPolicy::toMap).toList();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("flow", flow);
    map.put("policies", policiesAsMap());
    map.put("enabled", enabled);
    return map;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
