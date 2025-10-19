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

package org.idp.server.core.openid.token.handler.tokenrevocation.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class TokenRevocationResponse {
  TokenRevocationRequestStatus status;
  OAuthToken oAuthToken;
  Map<String, Object> response;
  Map<String, String> headers;

  public TokenRevocationResponse(
      TokenRevocationRequestStatus status, OAuthToken oAuthToken, Map<String, Object> contents) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.response = contents;
    this.headers = createDefaultHeaders();
  }

  private Map<String, String> createDefaultHeaders() {
    Map<String, String> values = new HashMap<>();
    values.put("Content-Type", "application/json");
    values.put("Cache-Control", "no-store, no-cache, must-revalidate, private");
    values.put("Pragma", "no-cache");
    return values;
  }

  public TokenRevocationRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public Map<String, Object> response() {
    return response;
  }

  public Map<String, String> responseHeaders() {
    return headers;
  }

  public boolean isOK() {
    return status.isOK();
  }

  public DefaultSecurityEventType securityEventType() {
    if (!isOK()) {
      return DefaultSecurityEventType.revoke_token_failure;
    }

    return DefaultSecurityEventType.revoke_token_success;
  }

  public boolean hasOAuthToken() {
    return oAuthToken != null && oAuthToken.exists();
  }
}
