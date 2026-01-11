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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.system.config.SsrfProtectionConfig;
import org.idp.server.platform.system.config.TrustedProxyConfig;

/**
 * System-wide configuration that applies across all tenants.
 *
 * <p>This configuration is stored in a dedicated system_config table and is independent of tenant
 * data. It includes security settings, feature flags, and other application-level configurations.
 *
 * <h3>Configuration Categories</h3>
 *
 * <ul>
 *   <li><b>Security</b>: SSRF protection, trusted proxies, rate limiting, etc.
 *   <li><b>Features</b>: System-wide feature flags (future)
 *   <li><b>Integrations</b>: External service configurations (future)
 * </ul>
 */
public class SystemConfiguration implements JsonReadable {

  private SsrfProtectionConfig ssrfProtection;
  private TrustedProxyConfig trustedProxies;

  public SystemConfiguration() {
    this.ssrfProtection = SsrfProtectionConfig.defaultConfig();
    this.trustedProxies = TrustedProxyConfig.defaultConfig();
  }

  public SystemConfiguration(
      SsrfProtectionConfig ssrfProtection, TrustedProxyConfig trustedProxies) {
    this.ssrfProtection = ssrfProtection;
    this.trustedProxies = trustedProxies;
  }

  /** Creates a default system configuration with all default values. */
  public static SystemConfiguration defaultConfiguration() {
    return new SystemConfiguration(
        SsrfProtectionConfig.defaultConfig(), TrustedProxyConfig.defaultConfig());
  }

  /**
   * Creates a disabled system configuration.
   *
   * <p>Used when no configuration exists in the database. All protection features are disabled to
   * allow the application to work out-of-the-box without requiring initial configuration.
   */
  public static SystemConfiguration disabledConfiguration() {
    return new SystemConfiguration(
        SsrfProtectionConfig.disabled(), TrustedProxyConfig.defaultConfig());
  }

  @SuppressWarnings("unchecked")
  public static SystemConfiguration fromMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return defaultConfiguration();
    }

    SsrfProtectionConfig ssrfConfig = SsrfProtectionConfig.defaultConfig();
    Object ssrfObj = map.get("ssrf_protection");
    if (ssrfObj instanceof Map) {
      ssrfConfig = SsrfProtectionConfig.fromMap((Map<String, Object>) ssrfObj);
    }

    TrustedProxyConfig proxyConfig = TrustedProxyConfig.defaultConfig();
    Object proxyObj = map.get("trusted_proxies");
    if (proxyObj instanceof Map) {
      proxyConfig = TrustedProxyConfig.fromMap((Map<String, Object>) proxyObj);
    }

    return new SystemConfiguration(ssrfConfig, proxyConfig);
  }

  public SsrfProtectionConfig ssrf() {
    return ssrfProtection;
  }

  public TrustedProxyConfig trustedProxies() {
    return trustedProxies;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("ssrf_protection", ssrfProtection.toMap());
    map.put("trusted_proxies", trustedProxies.toMap());
    return map;
  }
}
