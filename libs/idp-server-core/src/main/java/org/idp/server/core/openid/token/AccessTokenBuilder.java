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

package org.idp.server.core.openid.token;

import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.TokenIssuer;
import org.idp.server.core.openid.oauth.type.oauth.TokenType;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AccessTokenBuilder {
  TenantIdentifier tenantIdentifier;
  TokenIssuer tokenIssuer;
  TokenType tokenType;
  AccessTokenEntity accessTokenEntity;
  AuthorizationGrant authorizationGrant;
  ClientCertificationThumbprint clientCertificationThumbprint;
  AccessTokenCustomClaims customClaims;
  CreatedAt createdAt;
  ExpiresIn expiresIn;
  ExpiresAt expiresAt;

  public AccessTokenBuilder(TenantIdentifier tenantIdentifier) {
    this.tenantIdentifier = tenantIdentifier;
  }

  public AccessTokenBuilder add(TokenIssuer tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
    return this;
  }

  public AccessTokenBuilder add(TokenType tokenType) {
    this.tokenType = tokenType;
    return this;
  }

  public AccessTokenBuilder add(AccessTokenEntity accessTokenEntity) {
    this.accessTokenEntity = accessTokenEntity;
    return this;
  }

  public AccessTokenBuilder add(AuthorizationGrant authorizationGrant) {
    this.authorizationGrant = authorizationGrant;
    return this;
  }

  public AccessTokenBuilder add(ClientCertificationThumbprint clientCertificationThumbprint) {
    this.clientCertificationThumbprint = clientCertificationThumbprint;
    return this;
  }

  public AccessTokenBuilder add(AccessTokenCustomClaims customClaims) {
    this.customClaims = customClaims;
    return this;
  }

  public AccessTokenBuilder add(CreatedAt createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public AccessTokenBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    return this;
  }

  public AccessTokenBuilder add(ExpiresAt expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public AccessToken build() {
    return new AccessToken(
        tenantIdentifier,
        tokenIssuer,
        tokenType,
        accessTokenEntity,
        authorizationGrant,
        clientCertificationThumbprint,
        customClaims,
        createdAt,
        expiresIn,
        expiresAt);
  }
}
