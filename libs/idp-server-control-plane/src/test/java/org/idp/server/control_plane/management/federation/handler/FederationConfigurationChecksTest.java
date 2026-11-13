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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationRule;
import org.idp.server.core.openid.federation.FederationConfigurationRules;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.junit.jupiter.api.Test;

class FederationConfigurationChecksTest {

  /** A type whose data names its configuration, so the name stays. */
  static class Fixed implements FederationConfigurationRule {
    @Override
    public FederationType type() {
      return new FederationType("fixed");
    }

    @Override
    public List<String> problems(FederationConfiguration configuration) {
      return configuration.payload().containsKey("broken") ? List.of("broken") : List.of();
    }

    @Override
    public Map<String, Long> dependents(Tenant tenant, FederationConfiguration configuration) {
      return Map.of("things", 2L);
    }

    @Override
    public boolean allowsRename() {
      return false;
    }
  }

  FederationConfigurationChecks checks =
      new FederationConfigurationChecks(new FederationConfigurationRules(List.of(new Fixed())));

  static FederationConfiguration config(String type, String name, Map<String, Object> payload) {
    return new FederationConfiguration("id", type, name, payload);
  }

  @Test
  void refusesAConfigurationItsRuleFindsUnusable() {
    assertThrows(
        InvalidRequestException.class,
        () -> checks.verifyUsable(config("fixed", "a", Map.of("broken", true))));
    checks.verifyUsable(config("fixed", "a", Map.of()));
    checks.verifyUsable(config("oidc", "a", Map.of("broken", true))); // no rule for oidc
  }

  @Test
  void keepsTheNameOfATypeThatAsksForIt() {
    assertThrows(
        InvalidRequestException.class,
        () -> checks.verifyUpdate(config("fixed", "a", Map.of()), config("fixed", "b", Map.of())));
    checks.verifyUpdate(config("oidc", "a", Map.of()), config("oidc", "b", Map.of()));
  }

  @Test
  void refusesMovingIntoOrOutOfATypeWithARule() {
    assertThrows(
        InvalidRequestException.class,
        () -> checks.verifyUpdate(config("oidc", "a", Map.of()), config("fixed", "a", Map.of())));
    assertThrows(
        InvalidRequestException.class,
        () -> checks.verifyUpdate(config("fixed", "a", Map.of()), config("oidc", "a", Map.of())));
  }

  @Test
  void reportsDependentsByName() {
    assertEquals(Map.of("things", 2L), checks.dependents(null, config("fixed", "a", Map.of())));
    assertTrue(checks.dependents(null, config("oidc", "a", Map.of())).isEmpty());
    assertEquals(
        2L, FederationConfigurationChecks.conflict(Map.of("things", 2L)).contents().get("things"));
  }
}
