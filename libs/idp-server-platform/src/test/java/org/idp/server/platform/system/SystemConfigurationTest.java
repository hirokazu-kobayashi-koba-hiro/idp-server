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

package org.idp.server.platform.system;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.system.config.SsrfProtectionConfig;
import org.idp.server.platform.system.config.TrustedProxyConfig;
import org.junit.jupiter.api.Test;

class SystemConfigurationTest {

  @Test
  void defaultConfiguration_shouldHaveDefaultSsrfConfig() {
    SystemConfiguration config = SystemConfiguration.defaultConfiguration();

    assertNotNull(config.ssrf());
    assertTrue(config.ssrf().isEnabled());
    assertFalse(config.ssrf().hasBypassHosts());
    assertFalse(config.ssrf().hasAllowedHosts());
  }

  @Test
  void defaultConfiguration_shouldHaveDefaultTrustedProxyConfig() {
    SystemConfiguration config = SystemConfiguration.defaultConfiguration();

    assertNotNull(config.trustedProxies());
    assertFalse(config.trustedProxies().isEnabled());
    assertFalse(config.trustedProxies().hasAddresses());
  }

  @Test
  void constructor_withConfigs_shouldStoreConfig() {
    Set<String> bypassHosts = Set.of("localhost", "mock-service");
    SsrfProtectionConfig ssrfConfig = new SsrfProtectionConfig(true, bypassHosts, null);
    TrustedProxyConfig proxyConfig = TrustedProxyConfig.trustPrivateNetworks();

    SystemConfiguration config = new SystemConfiguration(ssrfConfig, proxyConfig);

    assertTrue(config.ssrf().isBypassHost("localhost"));
    assertTrue(config.ssrf().isBypassHost("mock-service"));
    assertTrue(config.trustedProxies().isEnabled());
    assertTrue(config.trustedProxies().isTrustedProxy("10.0.0.1"));
  }

  @Test
  void fromMap_shouldParseSsrfProtection() {
    Map<String, Object> ssrfMap = new HashMap<>();
    ssrfMap.put("enabled", true);
    ssrfMap.put("bypass_hosts", List.of("localhost", "127.0.0.1"));
    ssrfMap.put("allowed_hosts", List.of("api.example.com"));

    Map<String, Object> map = new HashMap<>();
    map.put("ssrf_protection", ssrfMap);

    SystemConfiguration config = SystemConfiguration.fromMap(map);

    assertTrue(config.ssrf().isEnabled());
    assertTrue(config.ssrf().isBypassHost("localhost"));
    assertTrue(config.ssrf().isAllowedHost("api.example.com"));
  }

  @Test
  void fromMap_shouldParseTrustedProxies() {
    Map<String, Object> proxyMap = new HashMap<>();
    proxyMap.put("enabled", true);
    proxyMap.put("addresses", List.of("10.0.0.0/8", "192.168.1.1"));
    proxyMap.put("trusted_headers", List.of("X-Forwarded-For", "X-Real-IP"));

    Map<String, Object> map = new HashMap<>();
    map.put("trusted_proxies", proxyMap);

    SystemConfiguration config = SystemConfiguration.fromMap(map);

    assertTrue(config.trustedProxies().isEnabled());
    assertTrue(config.trustedProxies().isTrustedProxy("10.0.0.1"));
    assertTrue(config.trustedProxies().isTrustedProxy("192.168.1.1"));
    assertTrue(config.trustedProxies().isTrustedHeader("X-Forwarded-For"));
  }

  @Test
  void fromMap_withNullMap_shouldReturnDefault() {
    SystemConfiguration config = SystemConfiguration.fromMap(null);

    assertNotNull(config);
    assertTrue(config.ssrf().isEnabled());
  }

  @Test
  void fromMap_withEmptyMap_shouldReturnDefault() {
    SystemConfiguration config = SystemConfiguration.fromMap(Map.of());

    assertNotNull(config);
    assertTrue(config.ssrf().isEnabled());
  }

  @Test
  void toMap_shouldSerializeSsrfProtection() {
    Set<String> bypassHosts = Set.of("localhost");
    SsrfProtectionConfig ssrfConfig = new SsrfProtectionConfig(true, bypassHosts, null);
    TrustedProxyConfig proxyConfig = TrustedProxyConfig.trustPrivateNetworks();
    SystemConfiguration config = new SystemConfiguration(ssrfConfig, proxyConfig);

    Map<String, Object> map = config.toMap();

    assertNotNull(map.get("ssrf_protection"));
    @SuppressWarnings("unchecked")
    Map<String, Object> ssrfMap = (Map<String, Object>) map.get("ssrf_protection");
    assertEquals(true, ssrfMap.get("enabled"));

    assertNotNull(map.get("trusted_proxies"));
    @SuppressWarnings("unchecked")
    Map<String, Object> proxyMap = (Map<String, Object>) map.get("trusted_proxies");
    assertEquals(true, proxyMap.get("enabled"));
  }

  @Test
  void roundTrip_toMapAndFromMap_shouldPreserveData() {
    Set<String> bypassHosts = Set.of("localhost", "mock-service");
    Set<String> allowedHosts = Set.of("api.example.com");
    SsrfProtectionConfig ssrfConfig = new SsrfProtectionConfig(true, bypassHosts, allowedHosts);
    TrustedProxyConfig proxyConfig = TrustedProxyConfig.trustPrivateNetworks();
    SystemConfiguration original = new SystemConfiguration(ssrfConfig, proxyConfig);

    Map<String, Object> map = original.toMap();
    SystemConfiguration restored = SystemConfiguration.fromMap(map);

    assertEquals(original.ssrf().isEnabled(), restored.ssrf().isEnabled());
    assertEquals(original.ssrf().bypassHosts(), restored.ssrf().bypassHosts());
    assertEquals(original.ssrf().allowedHosts(), restored.ssrf().allowedHosts());
    assertEquals(original.trustedProxies().isEnabled(), restored.trustedProxies().isEnabled());
    assertEquals(original.trustedProxies().addresses(), restored.trustedProxies().addresses());
    assertEquals(
        original.trustedProxies().trustedHeaders(), restored.trustedProxies().trustedHeaders());
  }
}
