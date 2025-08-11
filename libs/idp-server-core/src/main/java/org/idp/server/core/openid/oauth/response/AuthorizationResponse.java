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

package org.idp.server.core.openid.oauth.response;

import org.idp.server.core.openid.oauth.type.extension.JarmPayload;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.IdToken;
import org.idp.server.core.openid.oauth.type.oidc.ResponseMode;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.platform.http.HttpQueryParams;

public class AuthorizationResponse {
  RedirectUri redirectUri;
  ResponseMode responseMode;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode;
  State state;
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  Scopes scopes;
  IdToken idToken;
  TokenIssuer tokenIssuer;
  JarmPayload jarmPayload;
  HttpQueryParams httpQueryParams;

  AuthorizationResponse(
      RedirectUri redirectUri,
      ResponseMode responseMode,
      ResponseModeValue responseModeValue,
      AuthorizationCode authorizationCode,
      State state,
      AccessToken accessToken,
      TokenType tokenType,
      ExpiresIn expiresIn,
      Scopes scopes,
      IdToken idToken,
      TokenIssuer tokenIssuer,
      JarmPayload jarmPayload,
      HttpQueryParams httpQueryParams) {
    this.redirectUri = redirectUri;
    this.responseMode = responseMode;
    this.responseModeValue = responseModeValue;
    this.authorizationCode = authorizationCode;
    this.state = state;
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.scopes = scopes;
    this.idToken = idToken;
    this.tokenIssuer = tokenIssuer;
    this.jarmPayload = jarmPayload;
    this.httpQueryParams = httpQueryParams;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public ResponseMode responseMode() {
    return responseMode;
  }

  public ResponseModeValue responseModeValue() {
    return responseModeValue;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean hasAuthorizationCode() {
    return authorizationCode.exists();
  }

  public State state() {
    return state;
  }

  public boolean hasState() {
    return state.exists();
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public boolean hasAccessToken() {
    return accessToken.exists();
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public Scopes scopes() {
    return scopes;
  }

  public IdToken idToken() {
    return idToken;
  }

  public boolean hasIdToken() {
    return idToken.exists();
  }

  public JarmPayload jarmPayload() {
    return jarmPayload;
  }

  public boolean hasJarm() {
    return jarmPayload.exists();
  }

  HttpQueryParams queryParams() {
    return httpQueryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), httpQueryParams.params());
  }
}
