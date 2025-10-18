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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CORS (Cross-Origin Resource Sharing) configuration
 *
 * <p>Encapsulates tenant-specific CORS settings for controlling cross-origin requests to the IdP
 * server.
 */
public class CorsConfiguration {

  private final List<String> allowOrigins;
  private final String allowHeaders;
  private final String allowMethods;
  private final boolean allowCredentials;

  public CorsConfiguration() {
    this.allowOrigins = List.of();
    this.allowHeaders = "Authorization, Content-Type, Accept, x-device-id";
    this.allowMethods = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    this.allowCredentials = true;
  }

  public CorsConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.allowOrigins = extractStringList(safeValues, "allow_origins", List.of());
    this.allowHeaders =
        extractString(
            safeValues, "allow_headers", "Authorization, Content-Type, Accept, x-device-id");
    this.allowMethods =
        extractString(safeValues, "allow_methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
    this.allowCredentials = extractBoolean(safeValues, "allow_credentials", true);
  }

  /**
   * Returns the list of allowed origins
   *
   * @return list of allowed origin URLs (default: empty list)
   */
  public List<String> allowOrigins() {
    return allowOrigins;
  }

  /**
   * Returns the allowed headers
   *
   * @return comma-separated list of allowed headers (default: Authorization, Content-Type, Accept,
   *     x-device-id)
   */
  public String allowHeaders() {
    return allowHeaders;
  }

  /**
   * Returns the allowed HTTP methods
   *
   * @return comma-separated list of allowed methods (default: GET, POST, PUT, PATCH, DELETE,
   *     OPTIONS)
   */
  public String allowMethods() {
    return allowMethods;
  }

  /**
   * Returns whether credentials are allowed
   *
   * @return true if credentials are allowed (default: true)
   */
  public boolean allowCredentials() {
    return allowCredentials;
  }

  /**
   * Returns the configuration as a map
   *
   * @return configuration map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("allow_origins", allowOrigins);
    map.put("allow_headers", allowHeaders);
    map.put("allow_methods", allowMethods);
    map.put("allow_credentials", allowCredentials);
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

  @SuppressWarnings("unchecked")
  private static List<String> extractStringList(
      Map<String, Object> values, String key, List<String> defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    return defaultValue;
  }
}
