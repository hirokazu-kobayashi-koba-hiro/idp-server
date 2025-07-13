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

package org.idp.server.core.oidc.id_token;

import org.idp.server.core.oidc.type.extension.CustomProperties;
import org.idp.server.core.oidc.type.oauth.AccessTokenEntity;
import org.idp.server.core.oidc.type.oauth.AuthorizationCode;
import org.idp.server.core.oidc.type.oauth.State;
import org.idp.server.core.oidc.type.oidc.Nonce;

public class IdTokenCustomClaimsBuilder {
  AuthorizationCode authorizationCode = new AuthorizationCode();
  AccessTokenEntity accessTokenEntity = new AccessTokenEntity();
  State state = new State();
  Nonce nonce = new Nonce();
  CustomProperties customProperties = new CustomProperties();

  public IdTokenCustomClaimsBuilder() {}

  public IdTokenCustomClaimsBuilder add(AuthorizationCode authorizationCode) {
    this.authorizationCode = authorizationCode;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(AccessTokenEntity accessTokenEntity) {
    this.accessTokenEntity = accessTokenEntity;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(State state) {
    this.state = state;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(Nonce nonce) {
    this.nonce = nonce;
    return this;
  }

  public IdTokenCustomClaimsBuilder add(CustomProperties customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public IdTokenCustomClaims build() {
    return new IdTokenCustomClaims(
        authorizationCode, accessTokenEntity, state, nonce, customProperties);
  }
}
