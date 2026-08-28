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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * UI and authorization page configuration
 *
 * <p>Encapsulates tenant-specific UI settings including custom signin/signup pages for the OAuth
 * authorization flow.
 */
public class UIConfiguration {

  /** Custom parameter naming the variant when the tenant does not configure another name. */
  static final String DEFAULT_VARIANT_PARAM = "view_version";

  private String baseUrl;
  private String signupPage;
  private String signinPage;
  private String variantParam;
  private Map<String, UIViewVariant> variants;

  public UIConfiguration() {
    this.baseUrl = null;
    this.signupPage = "/auth-views/signup/index.html";
    this.signinPage = "/auth-views/signin/index.html";
    this.variantParam = DEFAULT_VARIANT_PARAM;
    this.variants = Map.of();
  }

  public UIConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.baseUrl = extractString(safeValues, "base_url", null);
    this.signupPage = extractString(safeValues, "signup_page", "/auth-views/signup/index.html");
    this.signinPage = extractString(safeValues, "signin_page", "/auth-views/signin/index.html");
    this.variantParam = extractString(safeValues, "variant_param", DEFAULT_VARIANT_PARAM);
    this.variants = extractVariants(safeValues);
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
   * The custom parameter that names the variant to show.
   *
   * <p>Configurable so that a tenant already passing a parameter of this name for something else
   * can move the selection aside rather than collide with it.
   *
   * @return parameter name (default: view_version)
   */
  public String variantParam() {
    return variantParam;
  }

  /**
   * Checks if any variant is declared
   *
   * @return true when the tenant runs pages beside its default ones
   */
  public boolean hasVariants() {
    return variants != null && !variants.isEmpty();
  }

  /**
   * The variant a request asked for.
   *
   * <p>An unknown or blank name reads as an empty variant, which inherits every page and so lands
   * on the defaults. The authorization URL is public, so a name nobody declared has to be harmless
   * rather than an error.
   *
   * @param name variant name taken from the request
   * @return the declared variant, or an empty one
   */
  public UIViewVariant variant(String name) {
    if (name == null || name.isEmpty() || !hasVariants()) {
      return new UIViewVariant();
    }
    return variants.getOrDefault(name, new UIViewVariant());
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
    map.put("variant_param", variantParam);
    if (hasVariants()) {
      Map<String, Object> variantsMap = new LinkedHashMap<>();
      variants.forEach((name, variant) -> variantsMap.put(name, variant.toMap()));
      map.put("variants", variantsMap);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, UIViewVariant> extractVariants(Map<String, Object> values) {
    Object value = values.get("variants");
    if (!(value instanceof Map<?, ?> rawVariants)) {
      return Map.of();
    }
    Map<String, UIViewVariant> variants = new LinkedHashMap<>();
    rawVariants.forEach(
        (name, rawVariant) -> {
          if (rawVariant instanceof Map<?, ?> variantValues) {
            variants.put(
                String.valueOf(name), new UIViewVariant((Map<String, Object>) variantValues));
          }
        });
    return variants;
  }

  private static String extractString(Map<String, Object> values, String key, String defaultValue) {
    if (values == null || values.isEmpty() || !values.containsKey(key)) {
      return defaultValue;
    }
    Object value = values.get(key);
    return value != null ? value.toString() : defaultValue;
  }
}
