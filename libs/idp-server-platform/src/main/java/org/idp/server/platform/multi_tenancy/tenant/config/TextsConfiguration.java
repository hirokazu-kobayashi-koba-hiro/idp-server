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
 * Text customization configuration for Universal Login
 *
 * <p>Encapsulates tenant-specific text customizations including page titles, button labels, and
 * footer text.
 */
public class TextsConfiguration {

  private final String pageTitle;
  private final String signinButton;
  private final String signupButton;
  private final String footerText;

  public TextsConfiguration() {
    this.pageTitle = null;
    this.signinButton = null;
    this.signupButton = null;
    this.footerText = null;
  }

  public TextsConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.pageTitle = extractString(safeValues, "page_title", null);
    this.signinButton = extractString(safeValues, "signin_button", null);
    this.signupButton = extractString(safeValues, "signup_button", null);
    this.footerText = extractString(safeValues, "footer_text", null);
  }

  /**
   * Returns the page title
   *
   * @return page title or null if not configured
   */
  public String pageTitle() {
    return pageTitle;
  }

  /**
   * Returns the signin button label
   *
   * @return signin button label or null if not configured
   */
  public String signinButton() {
    return signinButton;
  }

  /**
   * Returns the signup button label
   *
   * @return signup button label or null if not configured
   */
  public String signupButton() {
    return signupButton;
  }

  /**
   * Returns the footer text
   *
   * @return footer text or null if not configured
   */
  public String footerText() {
    return footerText;
  }

  /**
   * Returns the configuration as a map
   *
   * @return configuration map (non-null values only)
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (pageTitle != null) {
      map.put("page_title", pageTitle);
    }
    if (signinButton != null) {
      map.put("signin_button", signinButton);
    }
    if (signupButton != null) {
      map.put("signup_button", signupButton);
    }
    if (footerText != null) {
      map.put("footer_text", footerText);
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
