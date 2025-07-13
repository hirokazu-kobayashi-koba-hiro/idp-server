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

import org.idp.server.core.oidc.type.oidc.IdToken;
import org.idp.server.core.oidc.type.verifiablecredential.CNonce;
import org.idp.server.core.oidc.type.verifiablecredential.CNonceExpiresIn;

public class OAuthTokenBuilder {
  OAuthTokenIdentifier identifier;
  AccessToken accessToken = new AccessToken();
  RefreshToken refreshToken = new RefreshToken();
  IdToken idToken = new IdToken();
  CNonce cNonce = new CNonce();
  CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn();

  public OAuthTokenBuilder(OAuthTokenIdentifier identifier) {
    this.identifier = identifier;
  }

  public OAuthTokenBuilder add(AccessToken accessToken) {
    this.accessToken = accessToken;
    return this;
  }

  public OAuthTokenBuilder add(RefreshToken refreshToken) {
    this.refreshToken = refreshToken;
    return this;
  }

  public OAuthTokenBuilder add(IdToken idToken) {
    this.idToken = idToken;
    return this;
  }

  public OAuthTokenBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    return this;
  }

  public OAuthTokenBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    return this;
  }

  public OAuthToken build() {
    return new OAuthToken(identifier, accessToken, refreshToken, idToken, cNonce, cNonceExpiresIn);
  }
}
