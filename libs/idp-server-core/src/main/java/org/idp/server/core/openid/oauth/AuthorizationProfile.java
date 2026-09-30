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

package org.idp.server.core.openid.oauth;

import java.util.Set;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;

/** AuthorizationProfile */
public enum AuthorizationProfile {
  OAUTH2,
  OIDC,
  FAPI_BASELINE,
  FAPI_ADVANCE,
  FAPI_2_0,
  UNDEFINED;

  /**
   * Decides the profile from the scopes of a request. The FAPI profiles are selected by the scopes
   * the tenant assigns to them ({@code fapi20_scopes}, {@code fapi_advance_scopes}, {@code
   * fapi_baseline_scopes}), in that order of precedence.
   *
   * <p>Used for an authorization request and again for a refresh token request, which carries no
   * profile of its own and is decided from the scopes of the grant it refreshes.
   */
  public static AuthorizationProfile of(
      Set<String> scopes, AuthorizationServerConfiguration authorizationServerConfiguration) {
    if (authorizationServerConfiguration.hasFapi20Scope(scopes)) {
      return FAPI_2_0;
    }
    if (authorizationServerConfiguration.hasFapiAdvanceScope(scopes)) {
      return FAPI_ADVANCE;
    }
    if (authorizationServerConfiguration.hasFapiBaselineScope(scopes)) {
      return FAPI_BASELINE;
    }
    if (scopes.contains("openid")) {
      return OIDC;
    }
    return OAUTH2;
  }

  public boolean isOAuth2() {
    return this == OAUTH2;
  }

  public boolean isOidc() {
    return this == OIDC;
  }

  public boolean isFapiBaseline() {
    return this == FAPI_BASELINE;
  }

  public boolean isFapiAdvance() {
    return this == FAPI_ADVANCE;
  }

  public boolean isFapi20() {
    return this == FAPI_2_0;
  }

  public boolean isDefined() {
    return this != UNDEFINED;
  }
}
