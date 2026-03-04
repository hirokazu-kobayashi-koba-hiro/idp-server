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
 * UI and authorization page configuration
 *
 * <p>Encapsulates tenant-specific UI settings including custom signin/signup pages, branding,
 * texts, and layout customization for the OAuth authorization flow (Universal Login).
 */
public class UIConfiguration {

  private final String baseUrl;
  private final String signupPage;
  private final String signinPage;
  private final BrandingConfiguration branding;
  private final TextsConfiguration texts;
  private final LayoutConfiguration layout;
  private final String customCss;

  public UIConfiguration() {
    this.baseUrl = null;
    this.signupPage = "/auth-views/signup/index.html";
    this.signinPage = "/auth-views/signin/index.html";
    this.branding = new BrandingConfiguration();
    this.texts = new TextsConfiguration();
    this.layout = new LayoutConfiguration();
    this.customCss = null;
  }

  public UIConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.baseUrl = extractString(safeValues, "base_url", null);
    this.signupPage = extractString(safeValues, "signup_page", "/auth-views/signup/index.html");
    this.signinPage = extractString(safeValues, "signin_page", "/auth-views/signin/index.html");

    // New customization fields
    this.branding =
        safeValues.containsKey("branding")
            ? new BrandingConfiguration(castToMap(safeValues.get("branding")))
            : new BrandingConfiguration();
    this.texts =
        safeValues.containsKey("texts")
            ? new TextsConfiguration(castToMap(safeValues.get("texts")))
            : new TextsConfiguration();
    this.layout =
        safeValues.containsKey("layout")
            ? new LayoutConfiguration(castToMap(safeValues.get("layout")))
            : new LayoutConfiguration();
    this.customCss = extractString(safeValues, "custom_css", null);
  }

  /**
   * Returns the base URL for UI hosting
   *
   * @return base URL or null if not configured
   */
  public String baseUrl() {
    return baseUrl;
  }

  /**
   * Checks if base URL is configured
   *
   * @return true if base URL is set and not empty
   */
  public boolean hasBaseUrl() {
    return baseUrl != null && !baseUrl.isEmpty();
  }

  /**
   * Returns the custom signup page path
   *
   * @return signup page path (default: /auth-views/signup/index.html)
   */
  public String signupPage() {
    return signupPage;
  }

  /**
   * Returns the custom signin page path
   *
   * @return signin page path (default: /auth-views/signin/index.html)
   */
  public String signinPage() {
    return signinPage;
  }

  /**
   * Returns the branding configuration
   *
   * @return branding configuration
   */
  public BrandingConfiguration branding() {
    return branding;
  }

  /**
   * Returns the texts configuration
   *
   * @return texts configuration
   */
  public TextsConfiguration texts() {
    return texts;
  }

  /**
   * Returns the layout configuration
   *
   * @return layout configuration
   */
  public LayoutConfiguration layout() {
    return layout;
  }

  /**
   * Returns the custom CSS
   *
   * @return custom CSS or null if not configured
   */
  public String customCss() {
    return customCss;
  }

  /**
   * Returns the configuration as a map
   *
   * @return configuration map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (baseUrl != null) {
      map.put("base_url", baseUrl);
    }
    map.put("signup_page", signupPage);
    map.put("signin_page", signinPage);

    // New customization fields
    Map<String, Object> brandingMap = branding.toMap();
    if (!brandingMap.isEmpty()) {
      map.put("branding", brandingMap);
    }
    Map<String, Object> textsMap = texts.toMap();
    if (!textsMap.isEmpty()) {
      map.put("texts", textsMap);
    }
    map.put("layout", layout.toMap());
    if (customCss != null) {
      map.put("custom_css", customCss);
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castToMap(Object value) {
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    return new HashMap<>();
  }
}
