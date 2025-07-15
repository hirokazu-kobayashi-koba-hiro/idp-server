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

import java.time.LocalDateTime;
import java.util.Objects;
import org.idp.server.core.oidc.configuration.client.ClientAttributes;
import org.idp.server.core.oidc.configuration.client.ClientIdentifier;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.type.extension.CreatedAt;
import org.idp.server.core.oidc.type.extension.CustomProperties;
import org.idp.server.core.oidc.type.extension.ExpiresAt;
import org.idp.server.core.oidc.type.oauth.*;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class AccessToken {
  TenantIdentifier tenantIdentifier;
  TokenIssuer tokenIssuer;
  TokenType tokenType;
  AccessTokenEntity accessTokenEntity;
  AuthorizationGrant authorizationGrant;
  ClientCertificationThumbprint clientCertificationThumbprint;
  CreatedAt createdAt;
  ExpiresIn expiresIn;
  ExpiresAt expiresAt;

  public AccessToken() {}

  public AccessToken(
      TenantIdentifier tenantIdentifier,
      TokenIssuer tokenIssuer,
      TokenType tokenType,
      AccessTokenEntity accessTokenEntity,
      AuthorizationGrant authorizationGrant,
      ClientCertificationThumbprint clientCertificationThumbprint,
      CreatedAt createdAt,
      ExpiresIn expiresIn,
      ExpiresAt expiresAt) {
    this.tenantIdentifier = tenantIdentifier;
    this.tokenIssuer = tokenIssuer;
    this.tokenType = tokenType;
    this.accessTokenEntity = accessTokenEntity;
    this.authorizationGrant = authorizationGrant;
    this.clientCertificationThumbprint = clientCertificationThumbprint;
    this.createdAt = createdAt;
    this.expiresIn = expiresIn;
    this.expiresAt = expiresAt;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public AccessTokenEntity accessTokenEntity() {
    return accessTokenEntity;
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public ClientCertificationThumbprint clientCertificationThumbprint() {
    return clientCertificationThumbprint;
  }

  public boolean hasClientCertification() {
    return clientCertificationThumbprint.exists();
  }

  public boolean isSenderConstrained() {
    return clientCertificationThumbprint.exists();
  }

  public boolean hasSubject() {
    return authorizationGrant.hasUser();
  }

  public Subject subject() {
    return authorizationGrant.subject();
  }

  public CreatedAt createdAt() {
    return createdAt;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public ExpiresAt expiresAt() {
    return expiresAt;
  }

  public boolean isExpired(LocalDateTime other) {
    return expiresAt.isExpire(other);
  }

  public boolean exists() {
    return Objects.nonNull(accessTokenEntity) && accessTokenEntity.exists();
  }

  public ClientIdentifier clientIdentifier() {
    return authorizationGrant.clientIdentifier();
  }

  public RequestedClientId requestedClientId() {
    return authorizationGrant.requestedClientId();
  }

  public Scopes scopes() {
    return authorizationGrant.scopes();
  }

  public boolean hasCustomProperties() {
    return authorizationGrant.hasCustomProperties();
  }

  public CustomProperties customProperties() {
    return authorizationGrant.customProperties();
  }

  public boolean hasAuthorizationDetails() {
    return authorizationGrant.hasAuthorizationDetails();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationGrant.authorizationDetails();
  }

  public boolean matchThumbprint(ClientCertificationThumbprint thumbprint) {
    return clientCertificationThumbprint.equals(thumbprint);
  }

  public User user() {
    return authorizationGrant.user();
  }

  public ClientAttributes clientAttributes() {
    return authorizationGrant.clientAttributes();
  }

  public boolean isClientCredentialsGrant() {
    return authorizationGrant.isClientCredentialsGrant();
  }

  public boolean isOneshotToken() {
    return authorizationGrant.isOneshotToken();
  }
}
