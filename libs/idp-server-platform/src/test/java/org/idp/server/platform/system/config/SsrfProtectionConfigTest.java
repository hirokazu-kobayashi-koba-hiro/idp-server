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

package org.idp.server.platform.system.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SsrfProtectionConfigTest {

  @Test
  void defaultConfig_shouldBeEnabled() {
    SsrfProtectionConfig config = SsrfProtectionConfig.defaultConfig();

    assertTrue(config.isEnabled());
    assertFalse(config.hasBypassHosts());
    assertFalse(config.hasAllowedHosts());
  }

  @Test
  void disabled_shouldReturnDisabledConfig() {
    SsrfProtectionConfig config = SsrfProtectionConfig.disabled();

    assertFalse(config.isEnabled());
  }

  @Test
  void constructor_withBypassHosts_shouldStoreHosts() {
    Set<String> bypassHosts = Set.of("localhost", "127.0.0.1", "mock-service");
    SsrfProtectionConfig config = new SsrfProtectionConfig(true, bypassHosts, null);

    assertTrue(config.isEnabled());
    assertTrue(config.hasBypassHosts());
    assertEquals(3, config.bypassHosts().size());
    assertTrue(config.isBypassHost("localhost"));
    assertTrue(config.isBypassHost("127.0.0.1"));
    assertTrue(config.isBypassHost("mock-service"));
    assertFalse(config.isBypassHost("example.com"));
  }

  @Test
  void isBypassHost_shouldBeCaseInsensitive() {
    Set<String> bypassHosts = Set.of("localhost", "Mock-Service");
    SsrfProtectionConfig config = new SsrfProtectionConfig(true, bypassHosts, null);

    assertTrue(config.isBypassHost("LOCALHOST"));
    assertTrue(config.isBypassHost("mock-service"));
    assertTrue(config.isBypassHost("MOCK-SERVICE"));
  }

  @Test
  void constructor_withAllowedHosts_shouldStoreHosts() {
    Set<String> allowedHosts = Set.of("api.example.com", "cdn.example.com");
    SsrfProtectionConfig config = new SsrfProtectionConfig(true, null, allowedHosts);

    assertTrue(config.hasAllowedHosts());
    assertTrue(config.isAllowedHost("api.example.com"));
    assertTrue(config.isAllowedHost("cdn.example.com"));
    assertFalse(config.isAllowedHost("evil.com"));
  }

  @Test
  void isAllowedHost_withNoAllowlist_shouldAllowAll() {
    SsrfProtectionConfig config = SsrfProtectionConfig.defaultConfig();

    assertTrue(config.isAllowedHost("any-host.com"));
    assertTrue(config.isAllowedHost("another-host.com"));
  }

  @Test
  void fromMap_shouldParseCorrectly() {
    Map<String, Object> map = new HashMap<>();
    map.put("enabled", true);
    map.put("bypass_hosts", List.of("localhost", "mock-server"));
    map.put("allowed_hosts", List.of("api.example.com"));

    SsrfProtectionConfig config = SsrfProtectionConfig.fromMap(map);

    assertTrue(config.isEnabled());
    assertTrue(config.isBypassHost("localhost"));
    assertTrue(config.isBypassHost("mock-server"));
    assertTrue(config.isAllowedHost("api.example.com"));
  }

  @Test
  void fromMap_withNullMap_shouldReturnDefault() {
    SsrfProtectionConfig config = SsrfProtectionConfig.fromMap(null);

    assertTrue(config.isEnabled());
    assertFalse(config.hasBypassHosts());
  }

  @Test
  void fromMap_withEmptyMap_shouldReturnDefault() {
    SsrfProtectionConfig config = SsrfProtectionConfig.fromMap(Map.of());

    assertTrue(config.isEnabled());
    assertFalse(config.hasBypassHosts());
  }

  @Test
  void toMap_shouldSerializeCorrectly() {
    Set<String> bypassHosts = Set.of("localhost");
    Set<String> allowedHosts = Set.of("api.example.com");
    SsrfProtectionConfig config = new SsrfProtectionConfig(true, bypassHosts, allowedHosts);

    Map<String, Object> map = config.toMap();

    assertEquals(true, map.get("enabled"));
    assertTrue(((List<?>) map.get("bypass_hosts")).contains("localhost"));
    assertTrue(((List<?>) map.get("allowed_hosts")).contains("api.example.com"));
  }

  @Test
  void roundTrip_toMapAndFromMap_shouldPreserveData() {
    Set<String> bypassHosts = Set.of("localhost", "127.0.0.1");
    Set<String> allowedHosts = Set.of("api.example.com", "cdn.example.com");
    SsrfProtectionConfig original = new SsrfProtectionConfig(true, bypassHosts, allowedHosts);

    Map<String, Object> map = original.toMap();
    SsrfProtectionConfig restored = SsrfProtectionConfig.fromMap(map);

    assertEquals(original.isEnabled(), restored.isEnabled());
    assertEquals(original.bypassHosts(), restored.bypassHosts());
    assertEquals(original.allowedHosts(), restored.allowedHosts());
  }
}
