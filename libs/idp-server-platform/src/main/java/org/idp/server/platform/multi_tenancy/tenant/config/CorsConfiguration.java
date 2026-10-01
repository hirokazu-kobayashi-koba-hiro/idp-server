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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CORS (Cross-Origin Resource Sharing) configuration
 *
 * <p>Encapsulates tenant-specific CORS settings for controlling cross-origin requests to the IdP
 * server.
 */
public class CorsConfiguration {

  private List<String> allowOrigins;
  private String allowHeaders;
  private String allowMethods;
  private boolean allowCredentials;

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
   * Request headers idp-server's own authorization view sends, allowed whatever the tenant
   * configured. {@code x-view-binding} is how a view served from another site identifies itself on
   * its calls; leaving it to each tenant's {@code allow_headers} would break that mode for every
   * tenant whose list was stored before the header existed.
   */
  static final List<String> ALWAYS_ALLOWED_HEADERS = List.of("x-view-binding");

  /** The configured {@code allow_headers}, with the headers idp-server always needs added. */
  public String effectiveAllowHeaders() {
    String configured = allowHeaders != null ? allowHeaders : "";
    Set<String> present = new HashSet<>();
    for (String header : configured.split(",")) {
      if (!header.isBlank()) {
        present.add(header.trim().toLowerCase(Locale.ROOT));
      }
    }
    StringBuilder builder = new StringBuilder(configured.trim());
    for (String header : ALWAYS_ALLOWED_HEADERS) {
      if (!present.contains(header)) {
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(header);
      }
    }
    return builder.toString();
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
