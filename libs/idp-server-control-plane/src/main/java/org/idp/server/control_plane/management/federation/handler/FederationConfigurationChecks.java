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
package org.idp.server.control_plane.management.federation.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationRules;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Applies the rule a feature contributes for a configuration's type. Types without a rule are
 * stored as given, as before.
 */
class FederationConfigurationChecks {

  FederationConfigurationRules rules;

  FederationConfigurationChecks(FederationConfigurationRules rules) {
    this.rules = rules;
  }

  /** Refuses a configuration its type's rule finds unusable. */
  void verifyUsable(FederationConfiguration configuration) {
    rules
        .of(configuration.type())
        .ifPresent(
            rule -> {
              List<String> problems = rule.problems(configuration);
              if (!problems.isEmpty()) {
                throw new InvalidRequestException(
                    "Federation configuration is not usable as " + configuration.typeName() + ".",
                    problems);
              }
            });
  }

  /**
   * Refuses moving a configuration into or out of a type with a rule, and renaming one whose rule
   * keeps the name fixed. Data that belongs to a type names its configuration by type and name.
   */
  void verifyUpdate(FederationConfiguration before, FederationConfiguration after) {
    if (!before.type().equals(after.type())
        && (rules.governs(before.type()) || rules.governs(after.type()))) {
      throw new InvalidRequestException(
          "type cannot be changed for " + before.typeName() + " or " + after.typeName() + ".");
    }
    boolean renamed = !before.ssoProvider().name().equals(after.ssoProvider().name());
    if (renamed && rules.of(before.type()).map(rule -> !rule.allowsRename()).orElse(false)) {
      throw new InvalidRequestException(
          "sso_provider cannot be changed for " + before.typeName() + ".");
    }
  }

  /** What still depends on the configuration; empty when it may be deleted. */
  Map<String, Long> dependents(Tenant tenant, FederationConfiguration configuration) {
    return rules
        .of(configuration.type())
        .map(rule -> rule.dependents(tenant, configuration))
        .orElse(Map.of());
  }

  /** The 409 for a deletion that would leave dependent data behind, with what depends, by name. */
  static FederationConfigManagementResponse conflict(Map<String, Long> dependents) {
    Map<String, Object> contents = new HashMap<>(dependents);
    contents.put("error", "conflict");
    contents.put(
        "error_description",
        "Data still depends on this federation configuration. Remove it first.");
    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.CONFLICT, contents);
  }
}
