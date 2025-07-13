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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.type.oauth.*;
import org.idp.server.core.oidc.type.oidc.IdToken;
import org.idp.server.core.oidc.type.verifiablecredential.CNonce;
import org.idp.server.core.oidc.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.platform.json.JsonConverter;

public class TokenResponseBuilder {
  AccessTokenEntity accessTokenEntity;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
  Scopes scopes = new Scopes();
  IdToken idToken = new IdToken();
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  CNonce cNonce = new CNonce();
  CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn();
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public TokenResponseBuilder() {}

  public TokenResponseBuilder add(AccessTokenEntity accessTokenEntity) {
    this.accessTokenEntity = accessTokenEntity;
    values.put("access_token", accessTokenEntity.value());
    return this;
  }

  public TokenResponseBuilder add(TokenType tokenType) {
    this.tokenType = tokenType;
    values.put("token_type", tokenType.name());
    return this;
  }

  public TokenResponseBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    values.put("expires_in", expiresIn.value());
    return this;
  }

  public TokenResponseBuilder add(RefreshTokenEntity refreshTokenEntity) {
    this.refreshTokenEntity = refreshTokenEntity;
    if (refreshTokenEntity.exists()) {
      values.put("refresh_token", refreshTokenEntity.value());
    }
    return this;
  }

  public TokenResponseBuilder add(Scopes scopes) {
    this.scopes = scopes;
    values.put("scope", scopes.toStringValues());
    return this;
  }

  public TokenResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    if (idToken.exists()) {
      values.put("id_token", idToken.value());
    }
    return this;
  }

  public TokenResponseBuilder add(AuthorizationDetails authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    if (authorizationDetails.exists()) {
      values.put("authorization_details", authorizationDetails.toMapValues());
    }
    return this;
  }

  public TokenResponseBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    if (cNonce.exists()) {
      values.put("c_nonce", cNonce.value());
    }
    return this;
  }

  public TokenResponseBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    if (cNonceExpiresIn.exists()) {
      values.put("c_nonce_expires_in", cNonceExpiresIn.value());
    }
    return this;
  }

  public TokenResponse build() {
    String contents = jsonConverter.write(values);
    return new TokenResponse(
        accessTokenEntity,
        tokenType,
        expiresIn,
        refreshTokenEntity,
        scopes,
        idToken,
        authorizationDetails,
        cNonce,
        cNonceExpiresIn,
        contents);
  }
}
