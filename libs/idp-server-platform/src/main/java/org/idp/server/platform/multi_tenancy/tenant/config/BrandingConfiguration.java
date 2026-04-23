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
 * Branding configuration for Universal Login
 *
 * <p>Encapsulates tenant-specific branding settings including logo, favicon, colors, and background
 * images.
 */
public class BrandingConfiguration {

  private final String logoUrl;
  private final String faviconUrl;
  private final String primaryColor;
  private final String backgroundColor;
  private final String backgroundImageUrl;

  public BrandingConfiguration() {
    this.logoUrl = null;
    this.faviconUrl = null;
    this.primaryColor = null;
    this.backgroundColor = null;
    this.backgroundImageUrl = null;
  }

  public BrandingConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.logoUrl = extractString(safeValues, "logo_url", null);
    this.faviconUrl = extractString(safeValues, "favicon_url", null);
    this.primaryColor = extractString(safeValues, "primary_color", null);
    this.backgroundColor = extractString(safeValues, "background_color", null);
    this.backgroundImageUrl = extractString(safeValues, "background_image_url", null);
  }

  /**
   * Returns the logo URL
   *
   * @return logo URL or null if not configured
   */
  public String logoUrl() {
    return logoUrl;
  }

  /**
   * Returns the favicon URL
   *
   * @return favicon URL or null if not configured
   */
  public String faviconUrl() {
    return faviconUrl;
  }

  /**
   * Returns the primary color (hex code)
   *
   * @return primary color or null if not configured
   */
  public String primaryColor() {
    return primaryColor;
  }

  /**
   * Returns the background color (hex code)
   *
   * @return background color or null if not configured
   */
  public String backgroundColor() {
    return backgroundColor;
  }

  /**
   * Returns the background image URL
   *
   * @return background image URL or null if not configured
   */
  public String backgroundImageUrl() {
    return backgroundImageUrl;
  }

  /**
   * Returns the configuration as a map
   *
   * @return configuration map (non-null values only)
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (logoUrl != null) {
      map.put("logo_url", logoUrl);
    }
    if (faviconUrl != null) {
      map.put("favicon_url", faviconUrl);
    }
    if (primaryColor != null) {
      map.put("primary_color", primaryColor);
    }
    if (backgroundColor != null) {
      map.put("background_color", backgroundColor);
    }
    if (backgroundImageUrl != null) {
      map.put("background_image_url", backgroundImageUrl);
    }
    return map;
  }

  private static String extractString(Map<String, Object> values, String key, String defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    return value != null ? value.toString() : defaultValue;
  }
}
