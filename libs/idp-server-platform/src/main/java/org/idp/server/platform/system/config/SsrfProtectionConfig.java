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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for SSRF (Server-Side Request Forgery) protection.
 *
 * <p>This configuration controls which hosts are allowed or bypassed during SSRF validation.
 *
 * <ul>
 *   <li><b>enabled</b>: Whether SSRF protection is active
 *   <li><b>bypassHosts</b>: Hosts that bypass SSRF checks (for development/internal services)
 *   <li><b>allowedHosts</b>: Explicit allowlist of external hosts (OWASP recommended)
 * </ul>
 */
public class SsrfProtectionConfig {

  private boolean enabled;
  private Set<String> bypassHosts;
  private Set<String> allowedHosts;

  public SsrfProtectionConfig() {
    this.enabled = true;
    this.bypassHosts = Collections.emptySet();
    this.allowedHosts = Collections.emptySet();
  }

  public SsrfProtectionConfig(boolean enabled, Set<String> bypassHosts, Set<String> allowedHosts) {
    this.enabled = enabled;
    this.bypassHosts = normalizeHosts(bypassHosts);
    this.allowedHosts = normalizeHosts(allowedHosts);
  }

  private static Set<String> normalizeHosts(Set<String> hosts) {
    if (hosts == null || hosts.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> normalized = new HashSet<>();
    for (String host : hosts) {
      normalized.add(host.toLowerCase());
    }
    return normalized;
  }

  /** Creates a default configuration with SSRF protection enabled and no bypass hosts. */
  public static SsrfProtectionConfig defaultConfig() {
    return new SsrfProtectionConfig(true, Collections.emptySet(), Collections.emptySet());
  }

  /** Creates a disabled configuration (for testing or special cases). */
  public static SsrfProtectionConfig disabled() {
    return new SsrfProtectionConfig(false, Collections.emptySet(), Collections.emptySet());
  }

  @SuppressWarnings("unchecked")
  public static SsrfProtectionConfig fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultConfig();
    }

    boolean enabled = (Boolean) map.getOrDefault("enabled", true);

    Set<String> bypassHosts = new HashSet<>();
    Object bypassObj = map.get("bypass_hosts");
    if (bypassObj instanceof List) {
      for (Object host : (List<?>) bypassObj) {
        bypassHosts.add(host.toString().toLowerCase());
      }
    }

    Set<String> allowedHosts = new HashSet<>();
    Object allowedObj = map.get("allowed_hosts");
    if (allowedObj instanceof List) {
      for (Object host : (List<?>) allowedObj) {
        allowedHosts.add(host.toString().toLowerCase());
      }
    }

    return new SsrfProtectionConfig(enabled, bypassHosts, allowedHosts);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Set<String> bypassHosts() {
    return Collections.unmodifiableSet(bypassHosts);
  }

  public Set<String> allowedHosts() {
    return Collections.unmodifiableSet(allowedHosts);
  }

  public boolean hasBypassHosts() {
    return bypassHosts != null && !bypassHosts.isEmpty();
  }

  public boolean hasAllowedHosts() {
    return allowedHosts != null && !allowedHosts.isEmpty();
  }

  public boolean isBypassHost(String host) {
    if (!hasBypassHosts()) {
      return false;
    }
    return bypassHosts.contains(host.toLowerCase());
  }

  public boolean isAllowedHost(String host) {
    if (!hasAllowedHosts()) {
      return true; // No allowlist means all external hosts are allowed
    }
    return allowedHosts.contains(host.toLowerCase());
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("enabled", enabled);
    map.put("bypass_hosts", List.copyOf(bypassHosts));
    map.put("allowed_hosts", List.copyOf(allowedHosts));
    return map;
  }
}
