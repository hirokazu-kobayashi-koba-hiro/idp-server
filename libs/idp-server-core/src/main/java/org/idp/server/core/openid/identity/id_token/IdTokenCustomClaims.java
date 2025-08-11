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

package org.idp.server.core.openid.identity.id_token;

import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.core.openid.oauth.type.oauth.State;
import org.idp.server.core.openid.oauth.type.oidc.Nonce;

public class IdTokenCustomClaims {
  AuthorizationCode authorizationCode;
  AccessTokenEntity accessTokenEntity;
  State state;
  Nonce nonce;
  CustomProperties customProperties;

  IdTokenCustomClaims(
      AuthorizationCode authorizationCode,
      AccessTokenEntity accessTokenEntity,
      State state,
      Nonce nonce,
      CustomProperties customProperties) {
    this.authorizationCode = authorizationCode;
    this.accessTokenEntity = accessTokenEntity;
    this.state = state;
    this.nonce = nonce;
    this.customProperties = customProperties;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean hasAuthorizationCode() {
    return authorizationCode.exists();
  }

  public AccessTokenEntity accessTokenValue() {
    return accessTokenEntity;
  }

  public boolean hasAccessTokenValue() {
    return accessTokenEntity.exists();
  }

  public State state() {
    return state;
  }

  public boolean hasState() {
    return state.exists();
  }

  public Nonce nonce() {
    return nonce;
  }

  public boolean hasNonce() {
    return nonce.exists();
  }

  public CustomProperties customProperties() {
    return customProperties;
  }

  public boolean hasCustomProperties() {
    return customProperties.exists();
  }
}
