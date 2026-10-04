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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.idp.server.core.openid.oauth.type.oauth.CustomParamSource;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationPolicy implements JsonReadable {

  int priority;
  String description;
  AuthenticationPolicyCondition conditions = new AuthenticationPolicyCondition();
  List<String> availableMethods = new ArrayList<>();
  Map<String, List<String>> acrMappingRules = new HashMap<>();
  Map<String, List<String>> levelOfAuthenticationScopes = new HashMap<>();
  AuthenticationResultConditionConfig successConditions = new AuthenticationResultConditionConfig();
  AuthenticationResultConditionConfig failureConditions = new AuthenticationResultConditionConfig();
  AuthenticationResultConditionConfig lockConditions = new AuthenticationResultConditionConfig();
  AuthenticationResultConditionConfig deviceRegistrationConditions =
      new AuthenticationResultConditionConfig();
  List<AuthenticationStepDefinition> stepDefinitions = new ArrayList<>();
  boolean authSessionBindingRequired = true; // default: enabled for security

  /**
   * Issue #1907: the sources of custom parameters the conditions may read ({@code
   * $.request.custom_params.*}). Unset means {@link #DEFAULT_TRUSTED_CUSTOM_PARAM_SOURCES}.
   */
  List<String> customParamsTrustedSources;

  /** The sources the end-user cannot change the value through. */
  public static final List<String> DEFAULT_TRUSTED_CUSTOM_PARAM_SOURCES =
      List.of(CustomParamSource.PUSHED.value(), CustomParamSource.REQUEST_OBJECT.value());

  public AuthenticationPolicy() {}

  public boolean allMatch(RequestedClientId requestedClientId, AcrValues acrValues, Scopes scopes) {
    return conditions.allMatch(requestedClientId, acrValues, scopes);
  }

  public int priority() {
    return priority;
  }

  public String description() {
    return description;
  }

  public boolean hasDescription() {
    return description != null && !description.isEmpty();
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

  public AuthenticationResultConditionConfig successConditions() {
    return successConditions;
  }

  public boolean hasSuccessConditions() {
    return successConditions != null && successConditions.exists();
  }

  public AuthenticationResultConditionConfig failureConditions() {
    return failureConditions;
  }

  public boolean hasFailureConditions() {
    return failureConditions != null;
  }

  public AuthenticationResultConditionConfig lockConditions() {
    return lockConditions;
  }

  public boolean hasLockConditions() {
    return lockConditions != null;
  }

  public AuthenticationResultConditionConfig deviceRegistrationConditions() {
    return deviceRegistrationConditions;
  }

  public boolean hasDeviceRegistrationConditions() {
    return deviceRegistrationConditions != null && deviceRegistrationConditions.exists();
  }

  public List<AuthenticationStepDefinition> stepDefinitions() {
    return stepDefinitions;
  }

  public List<Map<String, Object>> stepDefinitionsAsMap() {
    return stepDefinitions.stream()
        .map(AuthenticationStepDefinition::toMap)
        .collect(Collectors.toList());
  }

  public boolean hasStepDefinitions() {
    return stepDefinitions != null && !stepDefinitions.isEmpty();
  }

  public boolean authSessionBindingRequired() {
    return authSessionBindingRequired;
  }

  public boolean hasCustomParamsTrustedSources() {
    return customParamsTrustedSources != null;
  }

  public List<String> customParamsTrustedSources() {
    return hasCustomParamsTrustedSources()
        ? customParamsTrustedSources
        : DEFAULT_TRUSTED_CUSTOM_PARAM_SOURCES;
  }

  /**
   * @return the trusted sources. A name that is not a source is left out, so it trusts nothing; the
   *     management API refuses such a policy.
   */
  public Set<CustomParamSource> trustedCustomParamSources() {
    Set<CustomParamSource> sources = new HashSet<>();
    for (String name : customParamsTrustedSources()) {
      CustomParamSource source = CustomParamSource.of(name);
      if (source != null) {
        sources.add(source);
      }
    }
    return sources;
  }

  /**
   * @return what the management API refuses in this policy (Issue #1907): an unknown name in {@code
   *     custom_params_trusted_sources}, and a misused {@code value_path} in any condition
   */
  public List<String> violations() {
    List<String> violations = new ArrayList<>();
    if (hasCustomParamsTrustedSources()) {
      for (String name : customParamsTrustedSources) {
        if (CustomParamSource.of(name) == null) {
          violations.add("custom_params_trusted_sources has an unknown source: " + name);
        }
      }
    }
    violations.addAll(successConditions.valuePathViolations());
    violations.addAll(failureConditions.valuePathViolations());
    violations.addAll(lockConditions.valuePathViolations());
    violations.addAll(deviceRegistrationConditions.valuePathViolations());
    return violations;
  }

  public boolean exists() {
    return hasSuccessConditions();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("priority", priority);
    if (hasDescription()) map.put("description", description);
    if (hasPolicyConditions()) map.put("conditions", conditions.toMap());
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    if (hasAcrMappingRules()) map.put("acr_mapping_rules", acrMappingRules);
    if (hasLevelOfAuthenticationScopes())
      map.put("level_of_authentication_scopes", levelOfAuthenticationScopes);
    if (hasSuccessConditions()) map.put("success_conditions", successConditions.toMap());
    if (hasFailureConditions()) map.put("failure_conditions", failureConditions.toMap());
    if (hasLockConditions()) map.put("lock_conditions", lockConditions.toMap());
    if (hasDeviceRegistrationConditions())
      map.put("device_registration_conditions", deviceRegistrationConditions.toMap());
    if (hasStepDefinitions()) map.put("step_definitions", stepDefinitionsAsMap());
    map.put("auth_session_binding_required", authSessionBindingRequired);
    if (hasCustomParamsTrustedSources())
      map.put("custom_params_trusted_sources", customParamsTrustedSources);
    return map;
  }
}
