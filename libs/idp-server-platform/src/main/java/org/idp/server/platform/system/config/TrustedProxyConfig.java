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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for trusted proxy servers.
 *
 * <p>When the application runs behind a reverse proxy or load balancer, this configuration
 * specifies which proxy servers are trusted for forwarding client information via headers like
 * X-Forwarded-For, X-Forwarded-Proto, etc.
 *
 * <ul>
 *   <li><b>enabled</b>: Whether trusted proxy handling is active
 *   <li><b>addresses</b>: IP addresses or CIDR ranges of trusted proxies (e.g., "10.0.0.0/8",
 *       "192.168.1.1")
 *   <li><b>trustedHeaders</b>: Which forwarding headers to trust (e.g., "X-Forwarded-For",
 *       "X-Forwarded-Proto")
 * </ul>
 *
 * <h3>Usage</h3>
 *
 * <p>When a request comes from a trusted proxy:
 *
 * <ul>
 *   <li>The real client IP is extracted from X-Forwarded-For header
 *   <li>The original protocol (http/https) is extracted from X-Forwarded-Proto
 *   <li>The original host is extracted from X-Forwarded-Host
 * </ul>
 */
public class TrustedProxyConfig {

  private boolean enabled;
  private Set<String> addresses;
  private Set<String> trustedHeaders;

  public TrustedProxyConfig() {
    this.enabled = false;
    this.addresses = Collections.emptySet();
    this.trustedHeaders = defaultTrustedHeaders();
  }

  public TrustedProxyConfig(boolean enabled, Set<String> addresses, Set<String> trustedHeaders) {
    this.enabled = enabled;
    this.addresses = addresses != null ? new HashSet<>(addresses) : Collections.emptySet();
    this.trustedHeaders =
        trustedHeaders != null ? new HashSet<>(trustedHeaders) : defaultTrustedHeaders();
  }

  private static Set<String> defaultTrustedHeaders() {
    return Set.of("X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host", "X-Real-IP");
  }

  /** Creates a default configuration with trusted proxy disabled. */
  public static TrustedProxyConfig defaultConfig() {
    return new TrustedProxyConfig(false, Collections.emptySet(), defaultTrustedHeaders());
  }

  /** Creates a configuration that trusts all private network ranges. */
  public static TrustedProxyConfig trustPrivateNetworks() {
    Set<String> privateRanges =
        Set.of(
            "10.0.0.0/8", // Class A private
            "172.16.0.0/12", // Class B private
            "192.168.0.0/16", // Class C private
            "127.0.0.0/8" // Loopback
            );
    return new TrustedProxyConfig(true, privateRanges, defaultTrustedHeaders());
  }

  @SuppressWarnings("unchecked")
  public static TrustedProxyConfig fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultConfig();
    }

    boolean enabled = (Boolean) map.getOrDefault("enabled", false);

    Set<String> addresses = new HashSet<>();
    Object addressesObj = map.get("addresses");
    if (addressesObj instanceof List) {
      for (Object addr : (List<?>) addressesObj) {
        addresses.add(addr.toString());
      }
    }

    Set<String> trustedHeaders = new HashSet<>();
    Object headersObj = map.get("trusted_headers");
    if (headersObj instanceof List) {
      for (Object header : (List<?>) headersObj) {
        trustedHeaders.add(header.toString());
      }
    } else {
      trustedHeaders = defaultTrustedHeaders();
    }

    return new TrustedProxyConfig(enabled, addresses, trustedHeaders);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Set<String> addresses() {
    return Collections.unmodifiableSet(addresses);
  }

  public Set<String> trustedHeaders() {
    return Collections.unmodifiableSet(trustedHeaders);
  }

  public boolean hasAddresses() {
    return addresses != null && !addresses.isEmpty();
  }

  /**
   * Checks if a given IP address is from a trusted proxy.
   *
   * @param ipAddress the IP address to check
   * @return true if the IP is from a trusted proxy
   */
  public boolean isTrustedProxy(String ipAddress) {
    if (!enabled || !hasAddresses()) {
      return false;
    }

    try {
      InetAddress clientAddr = InetAddress.getByName(ipAddress);

      for (String trustedRange : addresses) {
        if (isInRange(clientAddr, trustedRange)) {
          return true;
        }
      }
    } catch (UnknownHostException e) {
      return false;
    }

    return false;
  }

  /**
   * Checks if a header should be trusted.
   *
   * @param headerName the header name
   * @return true if the header is in the trusted headers list
   */
  public boolean isTrustedHeader(String headerName) {
    if (!enabled) {
      return false;
    }
    return trustedHeaders.stream().anyMatch(h -> h.equalsIgnoreCase(headerName));
  }

  private boolean isInRange(InetAddress address, String cidr) {
    try {
      if (cidr.contains("/")) {
        String[] parts = cidr.split("/");
        InetAddress networkAddr = InetAddress.getByName(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);
        return isInSubnet(address, networkAddr, prefixLength);
      } else {
        // Single IP address
        InetAddress trustedAddr = InetAddress.getByName(cidr);
        return address.equals(trustedAddr);
      }
    } catch (UnknownHostException | NumberFormatException e) {
      return false;
    }
  }

  private boolean isInSubnet(InetAddress address, InetAddress network, int prefixLength) {
    byte[] addrBytes = address.getAddress();
    byte[] networkBytes = network.getAddress();

    if (addrBytes.length != networkBytes.length) {
      return false;
    }

    int fullBytes = prefixLength / 8;
    int remainingBits = prefixLength % 8;

    for (int i = 0; i < fullBytes; i++) {
      if (addrBytes[i] != networkBytes[i]) {
        return false;
      }
    }

    if (remainingBits > 0 && fullBytes < addrBytes.length) {
      int mask = 0xFF << (8 - remainingBits);
      if ((addrBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
        return false;
      }
    }

    return true;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("enabled", enabled);
    map.put("addresses", List.copyOf(addresses));
    map.put("trusted_headers", List.copyOf(trustedHeaders));
    return map;
  }
}
