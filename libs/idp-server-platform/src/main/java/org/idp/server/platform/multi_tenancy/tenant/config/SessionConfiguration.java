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

package org.idp.server.platform.multi_tenancy.tenant.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Session and cookie configuration
 *
 * <p>Encapsulates tenant-specific session management and cookie settings for secure session
 * handling in the IdP server.
 */
public class SessionConfiguration {

  private final String cookieName;
  private final String cookieSameSite;
  private final boolean useSecureCookie;
  private final boolean useHttpOnlyCookie;
  private final String cookiePath;
  private final int timeoutSeconds;

  public SessionConfiguration() {
    this.cookieName = null;
    this.cookieSameSite = "None";
    this.useSecureCookie = true;
    this.useHttpOnlyCookie = true;
    this.cookiePath = "/";
    this.timeoutSeconds = 3600;
  }

  public SessionConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.cookieName = extractString(safeValues, "cookie_name", null);
    this.cookieSameSite = extractString(safeValues, "cookie_same_site", "None");
    this.useSecureCookie = extractBoolean(safeValues, "use_secure_cookie", true);
    this.useHttpOnlyCookie = extractBoolean(safeValues, "use_http_only_cookie", true);
    this.cookiePath = extractString(safeValues, "cookie_path", "/");
    this.timeoutSeconds = extractInt(safeValues, "timeout_seconds", 3600);
  }

  /**
   * Returns the cookie name
   *
   * @return cookie name, or null if not configured (tenant-specific default will be generated)
   */
  public String cookieName() {
    return cookieName;
  }

  /**
   * Returns whether cookie name is configured
   *
   * @return true if cookie name is explicitly configured
   */
  public boolean hasCookieName() {
    return cookieName != null && !cookieName.isEmpty();
  }

  /**
   * Returns the cookie SameSite attribute
   *
   * @return SameSite attribute value (default: None)
   */
  public String cookieSameSite() {
    return cookieSameSite;
  }

  /**
   * Returns whether secure cookie should be used
   *
   * @return true if secure cookie is enabled (default: true)
   */
  public boolean useSecureCookie() {
    return useSecureCookie;
  }

  /**
   * Returns whether HttpOnly cookie should be used
   *
   * @return true if HttpOnly is enabled (default: true)
   */
  public boolean useHttpOnlyCookie() {
    return useHttpOnlyCookie;
  }

  /**
   * Returns the cookie path
   *
   * @return cookie path (default: /)
   */
  public String cookiePath() {
    return cookiePath;
  }

  /**
   * Returns the session timeout in seconds
   *
   * @return timeout in seconds (default: 3600)
   */
  public int timeoutSeconds() {
    return timeoutSeconds;
  }

  /**
   * Returns the configuration as a map
   *
   * @return configuration map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (cookieName != null) {
      map.put("cookie_name", cookieName);
    }
    map.put("cookie_same_site", cookieSameSite);
    map.put("use_secure_cookie", useSecureCookie);
    map.put("use_http_only_cookie", useHttpOnlyCookie);
    map.put("cookie_path", cookiePath);
    map.put("timeout_seconds", timeoutSeconds);
    return map;
  }

  private static String extractString(Map<String, Object> values, String key, String defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  private static boolean extractBoolean(
      Map<String, Object> values, String key, boolean defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    return defaultValue;
  }

  private static int extractInt(Map<String, Object> values, String key, int defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return defaultValue;
  }
}
