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


package org.idp.server.core.oidc.token.handler.token.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.TokenErrorResponse;
import org.idp.server.core.oidc.token.TokenResponse;
import org.idp.server.core.oidc.token.TokenResponseBuilder;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class TokenRequestResponse {
  TokenRequestStatus status;
  OAuthToken oAuthToken;
  TokenResponse tokenResponse;
  TokenErrorResponse errorResponse;
  Map<String, String> headers;

  public TokenRequestResponse(TokenRequestStatus status, OAuthToken oAuthToken) {
    this.status = status;
    this.oAuthToken = oAuthToken;
    this.tokenResponse =
        new TokenResponseBuilder()
            .add(oAuthToken.accessTokenEntity())
            .add(oAuthToken.refreshTokenEntity())
            .add(oAuthToken.scopes())
            .add(oAuthToken.tokenType())
            .add(oAuthToken.expiresIn())
            .add(oAuthToken.idToken())
            .add(oAuthToken.authorizationDetails())
            .add(oAuthToken.cNonce())
            .add(oAuthToken.cNonceExpiresIn())
            .build();
    this.errorResponse = new TokenErrorResponse();
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public TokenRequestResponse(TokenRequestStatus status, TokenErrorResponse errorResponse) {
    this.status = status;
    this.tokenResponse = new TokenResponseBuilder().build();
    this.errorResponse = errorResponse;
    Map<String, String> values = new HashMap<>();
    values.put("Content-Typ", "application/json");
    values.put("Cache-Control", "no-store");
    values.put("Pragma", "no-cache");
    this.headers = values;
  }

  public String contents() {
    if (status.isOK()) {
      return tokenResponse.contents();
    }
    return errorResponse.contents();
  }

  public Map<String, String> responseHeaders() {
    return headers;
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public TokenResponse tokenResponse() {
    return tokenResponse;
  }

  public TokenErrorResponse errorResponse() {
    return errorResponse;
  }

  public TokenRequestStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public boolean isOK() {
    return status.isOK();
  }

  public boolean hasIdToken() {
    return oAuthToken.hasIdToken();
  }

  public DefaultSecurityEventType securityEventType(TokenRequest tokenRequest) {
    if (!isOK()) {
      return DefaultSecurityEventType.issue_token_failure;
    }

    if (hasIdToken()) {
      return DefaultSecurityEventType.login_success;
    }

    if (tokenRequest.isRefreshTokenGrant()) {
      return DefaultSecurityEventType.refresh_token_success;
    }

    return DefaultSecurityEventType.issue_token_success;
  }
}
