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

class TrustedProxyConfigTest {

  @Test
  void defaultConfig_shouldBeDisabled() {
    TrustedProxyConfig config = TrustedProxyConfig.defaultConfig();

    assertFalse(config.isEnabled());
    assertFalse(config.hasAddresses());
    assertNotNull(config.trustedHeaders());
  }

  @Test
  void trustPrivateNetworks_shouldEnableAndSetPrivateRanges() {
    TrustedProxyConfig config = TrustedProxyConfig.trustPrivateNetworks();

    assertTrue(config.isEnabled());
    assertTrue(config.hasAddresses());
    assertTrue(config.addresses().contains("10.0.0.0/8"));
    assertTrue(config.addresses().contains("172.16.0.0/12"));
    assertTrue(config.addresses().contains("192.168.0.0/16"));
  }

  @Test
  void isTrustedProxy_withDisabledConfig_shouldReturnFalse() {
    TrustedProxyConfig config = TrustedProxyConfig.defaultConfig();

    assertFalse(config.isTrustedProxy("10.0.0.1"));
    assertFalse(config.isTrustedProxy("192.168.1.1"));
  }

  @Test
  void isTrustedProxy_withCidrRange_shouldMatchCorrectly() {
    TrustedProxyConfig config = TrustedProxyConfig.trustPrivateNetworks();

    // Should match 10.0.0.0/8
    assertTrue(config.isTrustedProxy("10.0.0.1"));
    assertTrue(config.isTrustedProxy("10.255.255.255"));

    // Should match 172.16.0.0/12
    assertTrue(config.isTrustedProxy("172.16.0.1"));
    assertTrue(config.isTrustedProxy("172.31.255.255"));

    // Should match 192.168.0.0/16
    assertTrue(config.isTrustedProxy("192.168.0.1"));
    assertTrue(config.isTrustedProxy("192.168.255.255"));

    // Should NOT match public IPs
    assertFalse(config.isTrustedProxy("8.8.8.8"));
    assertFalse(config.isTrustedProxy("1.1.1.1"));
  }

  @Test
  void isTrustedProxy_withSingleIp_shouldMatchExactly() {
    Set<String> addresses = Set.of("192.168.1.100");
    TrustedProxyConfig config = new TrustedProxyConfig(true, addresses, null);

    assertTrue(config.isTrustedProxy("192.168.1.100"));
    assertFalse(config.isTrustedProxy("192.168.1.101"));
  }

  @Test
  void isTrustedHeader_shouldCheckAgainstTrustedHeaders() {
    TrustedProxyConfig config = TrustedProxyConfig.trustPrivateNetworks();

    assertTrue(config.isTrustedHeader("X-Forwarded-For"));
    assertTrue(config.isTrustedHeader("x-forwarded-for")); // case insensitive
    assertTrue(config.isTrustedHeader("X-Forwarded-Proto"));
    assertTrue(config.isTrustedHeader("X-Real-IP"));

    assertFalse(config.isTrustedHeader("X-Custom-Header"));
  }

  @Test
  void isTrustedHeader_withDisabledConfig_shouldReturnFalse() {
    TrustedProxyConfig config = TrustedProxyConfig.defaultConfig();

    assertFalse(config.isTrustedHeader("X-Forwarded-For"));
  }

  @Test
  void fromMap_shouldParseCorrectly() {
    Map<String, Object> map = new HashMap<>();
    map.put("enabled", true);
    map.put("addresses", List.of("10.0.0.0/8", "192.168.1.1"));
    map.put("trusted_headers", List.of("X-Forwarded-For", "X-Custom-Header"));

    TrustedProxyConfig config = TrustedProxyConfig.fromMap(map);

    assertTrue(config.isEnabled());
    assertTrue(config.addresses().contains("10.0.0.0/8"));
    assertTrue(config.addresses().contains("192.168.1.1"));
    assertTrue(config.trustedHeaders().contains("X-Forwarded-For"));
    assertTrue(config.trustedHeaders().contains("X-Custom-Header"));
  }

  @Test
  void fromMap_withNullMap_shouldReturnDefault() {
    TrustedProxyConfig config = TrustedProxyConfig.fromMap(null);

    assertFalse(config.isEnabled());
  }

  @Test
  void fromMap_withEmptyMap_shouldReturnDefault() {
    TrustedProxyConfig config = TrustedProxyConfig.fromMap(Map.of());

    assertFalse(config.isEnabled());
  }

  @Test
  void toMap_shouldSerializeCorrectly() {
    Set<String> addresses = Set.of("10.0.0.0/8");
    Set<String> headers = Set.of("X-Forwarded-For");
    TrustedProxyConfig config = new TrustedProxyConfig(true, addresses, headers);

    Map<String, Object> map = config.toMap();

    assertEquals(true, map.get("enabled"));
    assertTrue(((List<?>) map.get("addresses")).contains("10.0.0.0/8"));
    assertTrue(((List<?>) map.get("trusted_headers")).contains("X-Forwarded-For"));
  }

  @Test
  void roundTrip_toMapAndFromMap_shouldPreserveData() {
    Set<String> addresses = Set.of("10.0.0.0/8", "192.168.0.0/16");
    Set<String> headers = Set.of("X-Forwarded-For", "X-Real-IP");
    TrustedProxyConfig original = new TrustedProxyConfig(true, addresses, headers);

    Map<String, Object> map = original.toMap();
    TrustedProxyConfig restored = TrustedProxyConfig.fromMap(map);

    assertEquals(original.isEnabled(), restored.isEnabled());
    assertEquals(original.addresses(), restored.addresses());
    assertEquals(original.trustedHeaders(), restored.trustedHeaders());
  }
}
