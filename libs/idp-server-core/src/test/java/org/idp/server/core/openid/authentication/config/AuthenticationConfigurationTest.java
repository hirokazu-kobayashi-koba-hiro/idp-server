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

package org.idp.server.core.openid.authentication.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticationConfigurationTest {

  @Test
  @DisplayName("Default constructor should set enabled to true")
  void testDefaultConstructorSetsEnabledTrue() {
    AuthenticationConfiguration config = new AuthenticationConfiguration();

    assertTrue(config.isEnabled());
  }

  @Test
  @DisplayName("Constructor with basic parameters should set enabled to true by default")
  void testConstructorWithBasicParametersSetsEnabledTrue() {
    AuthenticationConfiguration config =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>());

    assertTrue(config.isEnabled());
  }

  @Test
  @DisplayName("Constructor with enabled parameter should respect the value")
  void testConstructorWithEnabledParameter() {
    AuthenticationConfiguration enabledConfig =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), true);
    AuthenticationConfiguration disabledConfig =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), false);

    assertTrue(enabledConfig.isEnabled());
    assertFalse(disabledConfig.isEnabled());
  }

  @Test
  @DisplayName("isActive should return true when config exists and is enabled")
  void testIsActiveWhenExistsAndEnabled() {
    AuthenticationConfiguration config =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), true);

    assertTrue(config.isActive());
  }

  @Test
  @DisplayName("isActive should return false when config exists but is disabled")
  void testIsActiveWhenExistsButDisabled() {
    AuthenticationConfiguration config =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), false);

    assertFalse(config.isActive());
  }

  @Test
  @DisplayName("isActive should return false when config is enabled but does not exist")
  void testIsActiveWhenEnabledButNotExists() {
    AuthenticationConfiguration config =
        new AuthenticationConfiguration(
            null, "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), true);

    assertFalse(config.isActive());
  }

  @Test
  @DisplayName("toMap should include enabled field")
  void testToMapIncludesEnabled() {
    AuthenticationConfiguration enabledConfig =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), true);
    AuthenticationConfiguration disabledConfig =
        new AuthenticationConfiguration(
            "test-id", "test-type", new HashMap<>(), new HashMap<>(), new HashMap<>(), false);

    Map<String, Object> enabledMap = enabledConfig.toMap();
    Map<String, Object> disabledMap = disabledConfig.toMap();

    assertTrue((Boolean) enabledMap.get("enabled"));
    assertFalse((Boolean) disabledMap.get("enabled"));
  }
}
