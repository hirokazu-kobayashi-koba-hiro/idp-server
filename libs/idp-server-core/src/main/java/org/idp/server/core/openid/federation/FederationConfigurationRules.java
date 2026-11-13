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
package org.idp.server.core.openid.federation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

/** The rules features contribute, by type. A type without one is stored as given. */
public class FederationConfigurationRules {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationConfigurationRules.class);

  Map<FederationType, FederationConfigurationRule> rules;

  public FederationConfigurationRules(List<FederationConfigurationRule> rules) {
    this.rules = new HashMap<>();
    rules.forEach(rule -> this.rules.put(rule.type(), rule));
  }

  public static FederationConfigurationRules load(
      ApplicationComponentContainer applicationComponentContainer) {
    List<FederationConfigurationRule> rules =
        PluginLoader.loadFromInternalModule(FederationConfigurationRuleFactory.class).stream()
            .map(factory -> factory.create(applicationComponentContainer))
            .toList();
    rules.forEach(
        rule -> log.info("Federation configuration rule registered: type={}", rule.type().name()));
    return new FederationConfigurationRules(rules);
  }

  public Optional<FederationConfigurationRule> of(FederationType type) {
    return Optional.ofNullable(rules.get(type));
  }

  public boolean governs(FederationType type) {
    return rules.containsKey(type);
  }
}
