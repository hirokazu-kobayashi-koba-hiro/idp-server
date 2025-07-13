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

package org.idp.server.core.oidc.response;

import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.oidc.type.extension.JarmPayload;
import org.idp.server.core.oidc.type.extension.ResponseModeValue;
import org.idp.server.core.oidc.type.oauth.*;
import org.idp.server.core.oidc.type.oidc.IdToken;
import org.idp.server.core.oidc.type.oidc.ResponseMode;
import org.idp.server.core.oidc.type.verifiablepresentation.VpToken;
import org.idp.server.platform.http.HttpQueryParams;

public class AuthorizationResponseBuilder {
  RedirectUri redirectUri;
  ResponseMode responseMode;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode = new AuthorizationCode();
  State state = new State();
  AccessToken accessToken = new AccessToken();
  TokenType tokenType = TokenType.undefined;
  ExpiresIn expiresIn = new ExpiresIn();
  Scopes scopes = new Scopes();
  IdToken idToken = new IdToken();
  VpToken vpToken = new VpToken();
  TokenIssuer tokenIssuer;
  JarmPayload jarmPayload = new JarmPayload();
  HttpQueryParams httpQueryParams;

  public AuthorizationResponseBuilder(
      RedirectUri redirectUri,
      ResponseMode responseMode,
      ResponseModeValue responseModeValue,
      TokenIssuer tokenIssuer) {
    this.redirectUri = redirectUri;
    this.responseMode = responseMode;
    this.responseModeValue = responseModeValue;
    this.tokenIssuer = tokenIssuer;
    this.httpQueryParams = new HttpQueryParams();
    httpQueryParams.add("iss", tokenIssuer.value());
  }

  public AuthorizationResponseBuilder add(AuthorizationCode authorizationCode) {
    this.authorizationCode = authorizationCode;
    this.httpQueryParams.add("code", authorizationCode.value());
    return this;
  }

  public AuthorizationResponseBuilder add(State state) {
    this.state = state;
    this.httpQueryParams.add("state", state.value());
    return this;
  }

  public AuthorizationResponseBuilder add(AccessToken accessToken) {
    this.accessToken = accessToken;
    this.httpQueryParams.add("access_token", accessToken.accessTokenEntity().value());
    return this;
  }

  public AuthorizationResponseBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    this.httpQueryParams.add("expires_in", expiresIn.toStringValue());
    return this;
  }

  public AuthorizationResponseBuilder add(TokenType tokenType) {
    if (tokenType.isDefined()) {
      this.tokenType = tokenType;
      this.httpQueryParams.add("token_type", tokenType.name());
    }
    return this;
  }

  public AuthorizationResponseBuilder add(Scopes scopes) {
    this.scopes = scopes;
    this.httpQueryParams.add("scope", scopes.toStringValues());
    return this;
  }

  public AuthorizationResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    this.httpQueryParams.add("id_token", idToken.value());
    return this;
  }

  public AuthorizationResponseBuilder add(VpToken vpToken) {
    this.vpToken = vpToken;
    this.httpQueryParams.add("vp_token", vpToken.value());
    return this;
  }

  public AuthorizationResponseBuilder add(JarmPayload jarmPayload) {
    this.jarmPayload = jarmPayload;
    return this;
  }

  public AuthorizationResponse build() {
    // TODO consider
    if (jarmPayload.exists()) {
      this.httpQueryParams = new HttpQueryParams();
      this.httpQueryParams.add("response", jarmPayload.value());
    }
    return new AuthorizationResponse(
        redirectUri,
        responseMode,
        responseModeValue,
        authorizationCode,
        state,
        accessToken,
        tokenType,
        expiresIn,
        scopes,
        idToken,
        tokenIssuer,
        jarmPayload,
        httpQueryParams);
  }
}
