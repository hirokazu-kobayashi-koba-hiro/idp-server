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

package org.idp.server.federation.sso.oidc;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OidcTokenResult {

  int statusCode;
  Map<String, List<String>> headers;
  Map<String, Object> body;

  public OidcTokenResult() {}

  public OidcTokenResult(
      int statusCode, Map<String, List<String>> headers, Map<String, Object> body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public Map<String, Object> bodyAsMap() {
    return body;
  }

  public boolean exists() {
    return Objects.nonNull(body) && !body.isEmpty();
  }

  public String accessToken() {
    return (String) body.get("access_token");
  }

  public String refreshToken() {
    return (String) body.get("refresh_token");
  }

  public String idToken() {
    return (String) body.get("id_token");
  }

  public String expiresIn() {
    return (String) body.get("expires_in");
  }

  public String scope() {
    return (String) body.get("scope");
  }

  public String tokenType() {
    return (String) body.get("token_type");
  }

  public boolean hasAccessToken() {
    return body.containsKey("access_token");
  }

  public boolean hasRefreshToken() {
    return body.containsKey("refresh_token");
  }

  public boolean hasIdToken() {
    return body.containsKey("id_token");
  }

  public boolean hasExpiresIn() {
    return body.containsKey("expires_in");
  }

  public boolean hasScope() {
    return body.containsKey("scope");
  }

  public boolean hasTokenType() {
    return body.containsKey("token_type");
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) body.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return body.containsKey(key);
  }

  public boolean isError() {
    return statusCode >= 400;
  }
}
