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
 * One alternative set of authorization pages, selected per request.
 *
 * <p>A canary release runs the new pages beside the current ones rather than replacing them, so a
 * variant names only what differs. Anything it leaves out is inherited from {@link
 * UIConfiguration}, which lets a variant that only moves a path stay a single line.
 *
 * <p>The base URL is part of it because a variant can be a separate deployment rather than another
 * path on the same one. A variant on its own origin needs that origin in {@code
 * cors_config.allow_origins} as well.
 *
 * <p>An absent variant reads as an empty one rather than null, so the caller inherits everything
 * and lands on the default pages. That is what a request naming a variant the tenant does not
 * declare gets: the authorization URL is public, so an undeclared name has to be harmless.
 */
public class UIViewVariant {

  private String baseUrl;
  private String signupPage;
  private String signinPage;

  /** An empty variant, which inherits every page from the tenant's default configuration. */
  public UIViewVariant() {}

  public UIViewVariant(Map<String, Object> values) {
    Map<String, Object> safeValues = Objects.requireNonNullElseGet(values, HashMap::new);
    this.baseUrl = extractString(safeValues, "base_url");
    this.signupPage = extractString(safeValues, "signup_page");
    this.signinPage = extractString(safeValues, "signin_page");
  }

  public String baseUrl() {
    return baseUrl;
  }

  public boolean hasBaseUrl() {
    return baseUrl != null && !baseUrl.isEmpty();
  }

  public String signupPage() {
    return signupPage;
  }

  public boolean hasSignupPage() {
    return signupPage != null && !signupPage.isEmpty();
  }

  public String signinPage() {
    return signinPage;
  }

  public boolean hasSigninPage() {
    return signinPage != null && !signinPage.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasBaseUrl()) {
      map.put("base_url", baseUrl);
    }
    if (hasSignupPage()) {
      map.put("signup_page", signupPage);
    }
    if (hasSigninPage()) {
      map.put("signin_page", signinPage);
    }
    return map;
  }

  private static String extractString(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value != null ? value.toString() : null;
  }
}
