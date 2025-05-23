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

package org.idp.server.core.oidc.token;

import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.core.oidc.rar.AuthorizationDetails;

public class TokenResponse {
  AccessTokenEntity accessTokenEntity;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshTokenEntity refreshTokenEntity;
  Scopes scopes;
  IdToken idToken;
  AuthorizationDetails authorizationDetails;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  String contents;

  TokenResponse(
      AccessTokenEntity accessTokenEntity,
      TokenType tokenType,
      ExpiresIn expiresIn,
      RefreshTokenEntity refreshTokenEntity,
      Scopes scopes,
      IdToken idToken,
      AuthorizationDetails authorizationDetails,
      CNonce cNonce,
      CNonceExpiresIn cNonceExpiresIn,
      String contents) {
    this.accessTokenEntity = accessTokenEntity;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshTokenEntity = refreshTokenEntity;
    this.scopes = scopes;
    this.idToken = idToken;
    this.authorizationDetails = authorizationDetails;
    this.cNonce = cNonce;
    this.cNonceExpiresIn = cNonceExpiresIn;
    this.contents = contents;
  }

  public AccessTokenEntity accessToken() {
    return accessTokenEntity;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public RefreshTokenEntity refreshToken() {
    return refreshTokenEntity;
  }

  public IdToken idToken() {
    return idToken;
  }

  public Scopes scopes() {
    return scopes;
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public CNonce cNonce() {
    return cNonce;
  }

  public CNonceExpiresIn cNonceExpiresIn() {
    return cNonceExpiresIn;
  }

  public String contents() {
    return contents;
  }

  public boolean hasRefreshToken() {
    return refreshTokenEntity.exists();
  }
}
