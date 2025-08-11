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

/** AuthorizationProfile */
public enum AuthorizationProfile {
  OAUTH2,
  OIDC,
  FAPI_BASELINE,
  FAPI_ADVANCE,
  UNDEFINED;

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

  public boolean isDefined() {
    return this != UNDEFINED;
  }
}
