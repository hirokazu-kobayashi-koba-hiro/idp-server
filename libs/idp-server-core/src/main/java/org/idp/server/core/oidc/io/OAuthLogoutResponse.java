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


package org.idp.server.core.oidc.io;

import java.util.Map;

/** OAuthLogoutResponse */
public class OAuthLogoutResponse {
  OAuthLogoutStatus status;
  String redirectUriValue;

  public OAuthLogoutResponse() {}

  public OAuthLogoutResponse(OAuthLogoutStatus status, String redirectUriValue) {
    this.status = status;
    this.redirectUriValue = redirectUriValue;
  }

  public Map<String, Object> contents() {
    return Map.of("redirect_uri", redirectUriValue);
  }

  public OAuthLogoutStatus status() {
    return status;
  }

  public String redirectUriValue() {
    return redirectUriValue;
  }
}
