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
import java.util.List;
import java.util.Objects;
import org.idp.server.basic.type.extension.ExpiresAt;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.IdToken;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OAuthToken {
  OAuthTokenIdentifier identifier;
  AccessToken accessToken;
  RefreshToken refreshToken;
  IdToken idToken;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;

  public OAuthToken() {}

  OAuthToken(
      OAuthTokenIdentifier identifier,
      AccessToken accessToken,
      RefreshToken refreshToken,
      IdToken idToken,
      CNonce cNonce,
      CNonceExpiresIn cNonceExpiresIn) {
    this.identifier = identifier;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.idToken = idToken;
    this.cNonce = cNonce;
    this.cNonceExpiresIn = cNonceExpiresIn;
  }

  public OAuthTokenIdentifier identifier() {
    return identifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return accessToken.tenantIdentifier();
  }

  public AuthorizationGrant authorizationGrant() {
    return accessToken.authorizationGrant();
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && identifier.exists();
  }

  public boolean isExpire(LocalDateTime other) {
    return accessToken.isExpired(other);
  }

  public AccessTokenEntity accessTokenEntity() {
    return accessToken.accessTokenEntity();
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public RefreshToken refreshToken() {
    return refreshToken;
  }

  public RefreshTokenEntity refreshTokenEntity() {
    return refreshToken.refreshTokenEntity();
  }

  public IdToken idToken() {
    return idToken;
  }

  public boolean hasRefreshToken() {
    return refreshToken.exists();
  }

  public Subject subject() {
    return authorizationGrant().subject();
  }

  public boolean hasOpenidScope() {
    return authorizationGrant().hasOpenidScope();
  }

  public Scopes scopes() {
    return accessToken.scopes();
  }

  public TokenType tokenType() {
    return accessToken.tokenType();
  }

  public ExpiresIn expiresIn() {
    return accessToken.expiresIn();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationGrant().authorizationDetails();
  }

  public boolean hasIdToken() {
    return idToken.exists();
  }

  public boolean hasClientCertification() {
    return accessToken.hasClientCertification();
  }

  public CNonce cNonce() {
    return cNonce;
  }

  public boolean hasCNonce() {
    return cNonce.exists();
  }

  public CNonceExpiresIn cNonceExpiresIn() {
    return cNonceExpiresIn;
  }

  public boolean hasCNonceExpiresIn() {
    return cNonceExpiresIn.exists();
  }

  public TokenIssuer tokenIssuer() {
    return accessToken.tokenIssuer();
  }

  public RequestedClientId requestedClientId() {
    return accessToken.requestedClientId();
  }

  public User user() {
    return accessToken.user();
  }

  public Client client() {
    return accessToken.client();
  }

  public String clientName() {
    return accessToken.client().nameValue();
  }

  public List<String> scopeAsList() {
    return accessToken.scopes().toStringList();
  }

  public boolean isClientCredentialsGrant() {
    return accessToken.isClientCredentialsGrant();
  }

  public boolean isOneshotToken() {
    return accessToken.isOneshotToken();
  }

  public ExpiresAt expiresAt() {
    if (!hasRefreshToken()) {
      return accessToken.expiresAt();
    }
    ExpiresAt accessTokenExpiresAt = accessToken.expiresAt();
    ExpiresAt refreshTokenExpiresAt = refreshToken.expiresAt();

    return refreshTokenExpiresAt.isAfter(accessTokenExpiresAt)
        ? refreshTokenExpiresAt
        : accessTokenExpiresAt;
  }
}
