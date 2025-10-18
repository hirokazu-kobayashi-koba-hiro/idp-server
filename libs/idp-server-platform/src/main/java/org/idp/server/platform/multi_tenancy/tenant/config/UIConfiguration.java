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
 * <p>Encapsulates tenant-specific UI settings including custom signin/signup pages for the OAuth
 * authorization flow.
 */
public class UIConfiguration {

  private final String signupPage;
  private final String signinPage;

  public UIConfiguration() {
    this.signupPage = "/auth-views/signup/index.html";
    this.signinPage = "/auth-views/signin/index.html";
  }

  public UIConfiguration(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.signupPage = extractString(safeValues, "signup_page", "/auth-views/signup/index.html");
    this.signinPage = extractString(safeValues, "signin_page", "/auth-views/signin/index.html");
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
   * Returns the configuration as a map
   *
   * @return configuration map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("signup_page", signupPage);
    map.put("signin_page", signinPage);
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
