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

package org.idp.server.account_linking;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * One external IdP account a user has linked, together with the tokens issued for it.
 *
 * <p>Tokens are held encrypted; this type never sees plaintext. Decryption belongs to the caller
 * that actually needs to replay the token, so that listing links does not decrypt anything.
 */
public class LinkedExternalAccount {

  LinkedExternalAccountIdentifier identifier;
  TenantIdentifier tenantIdentifier;
  UserIdentifier userIdentifier;
  ExternalIdpProvider provider;
  AccountAlias accountAlias;
  String federatedUserId;
  String federatedUsername;
  String scope;
  EncryptedData encryptedAccessToken;
  EncryptedData encryptedRefreshToken;
  String encryptionKeyId;
  LocalDateTime accessTokenExpiresAt;
  LocalDateTime refreshTokenExpiresAt;
  Map<String, Object> metadata;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;

  public LinkedExternalAccount() {}

  LinkedExternalAccount(Builder builder) {
    this.identifier = builder.identifier;
    this.tenantIdentifier = builder.tenantIdentifier;
    this.userIdentifier = builder.userIdentifier;
    this.provider = builder.provider;
    this.accountAlias = builder.accountAlias;
    this.federatedUserId = builder.federatedUserId;
    this.federatedUsername = builder.federatedUsername;
    this.scope = builder.scope;
    this.encryptedAccessToken = builder.encryptedAccessToken;
    this.encryptedRefreshToken = builder.encryptedRefreshToken;
    this.encryptionKeyId = builder.encryptionKeyId;
    this.accessTokenExpiresAt = builder.accessTokenExpiresAt;
    this.refreshTokenExpiresAt = builder.refreshTokenExpiresAt;
    this.metadata = builder.metadata;
    this.createdAt = builder.createdAt;
    this.updatedAt = builder.updatedAt;
  }

  /**
   * Materializes the link from a session that has just been claimed.
   *
   * @param session a session already transitioned to {@code CONSUMED}
   * @param accountAlias the alias assigned for this account
   */
  public static LinkedExternalAccount fromConsumedSession(
      AccountLinkingSession session, AccountAlias accountAlias, LocalDateTime now) {
    ParkedCredentials credentials = session.parkedCredentials();

    return new Builder()
        .identifier(new LinkedExternalAccountIdentifier(UUID.randomUUID().toString()))
        .tenantIdentifier(session.tenantIdentifier())
        .userIdentifier(session.userIdentifier())
        .provider(session.provider())
        .accountAlias(accountAlias)
        .federatedUserId(credentials.federatedUserId())
        .federatedUsername(credentials.federatedUsername())
        .scope(credentials.grantedScope())
        .encryptedAccessToken(credentials.encryptedAccessToken())
        .encryptedRefreshToken(credentials.encryptedRefreshToken())
        .encryptionKeyId(credentials.encryptionKeyId())
        .accessTokenExpiresAt(credentials.accessTokenExpiresAt())
        .refreshTokenExpiresAt(credentials.refreshTokenExpiresAt())
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * Returns a copy carrying the result of linking the same external account again.
   *
   * <p>Re-linking keeps the identifier and the alias: the alias appears in URLs the RP may have
   * stored, and the row is the same link, now holding freshly issued tokens and whatever scope the
   * user consented to this time.
   */
  public LinkedExternalAccount relinkedWith(ParkedCredentials credentials, LocalDateTime now) {
    return toBuilder()
        .federatedUsername(credentials.federatedUsername())
        .scope(credentials.grantedScope())
        .encryptedAccessToken(credentials.encryptedAccessToken())
        .encryptedRefreshToken(credentials.encryptedRefreshToken())
        .encryptionKeyId(credentials.encryptionKeyId())
        .accessTokenExpiresAt(credentials.accessTokenExpiresAt())
        .refreshTokenExpiresAt(credentials.refreshTokenExpiresAt())
        .updatedAt(now)
        .build();
  }

  /** Returns a copy carrying tokens obtained from a refresh. */
  public LinkedExternalAccount withRefreshedTokens(
      EncryptedData encryptedAccessToken,
      EncryptedData encryptedRefreshToken,
      LocalDateTime accessTokenExpiresAt,
      LocalDateTime refreshTokenExpiresAt,
      LocalDateTime now) {
    return toBuilder()
        .encryptedAccessToken(encryptedAccessToken)
        .encryptedRefreshToken(encryptedRefreshToken)
        .accessTokenExpiresAt(accessTokenExpiresAt)
        .refreshTokenExpiresAt(refreshTokenExpiresAt)
        .updatedAt(now)
        .build();
  }

  public boolean isAccessTokenExpired(LocalDateTime now) {
    return accessTokenExpiresAt == null || !now.isBefore(accessTokenExpiresAt);
  }

  public boolean hasRefreshToken() {
    return encryptedRefreshToken != null;
  }

  public boolean isRefreshTokenExpired(LocalDateTime now) {
    return refreshTokenExpiresAt != null && !now.isBefore(refreshTokenExpiresAt);
  }

  /** Whether a token request can still be satisfied without the user linking again. */
  public boolean isRelinkRequired(LocalDateTime now) {
    return isAccessTokenExpired(now) && (!hasRefreshToken() || isRefreshTokenExpired(now));
  }

  public LinkedExternalAccountIdentifier identifier() {
    return identifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  public ExternalIdpProvider provider() {
    return provider;
  }

  public AccountAlias accountAlias() {
    return accountAlias;
  }

  public String federatedUserId() {
    return federatedUserId;
  }

  public String federatedUsername() {
    return federatedUsername;
  }

  public String scope() {
    return scope;
  }

  public EncryptedData encryptedAccessToken() {
    return encryptedAccessToken;
  }

  public EncryptedData encryptedRefreshToken() {
    return encryptedRefreshToken;
  }

  public String encryptionKeyId() {
    return encryptionKeyId;
  }

  public LocalDateTime accessTokenExpiresAt() {
    return accessTokenExpiresAt;
  }

  public LocalDateTime refreshTokenExpiresAt() {
    return refreshTokenExpiresAt;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public boolean exists() {
    return identifier != null && identifier.exists();
  }

  /** Representation for the list and get responses. Deliberately carries no token material. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("alias", accountAlias.value());
    map.put("provider", provider.value());
    map.put("federated_username", federatedUsername);
    map.put("scope", scope);
    map.put("access_token_expires_at", toStringOrNull(accessTokenExpiresAt));
    map.put("refresh_token_expires_at", toStringOrNull(refreshTokenExpiresAt));
    map.put("created_at", toStringOrNull(createdAt));
    map.put("updated_at", toStringOrNull(updatedAt));
    return map;
  }

  private String toStringOrNull(LocalDateTime value) {
    return value == null ? null : value.toString();
  }

  public Builder toBuilder() {
    return new Builder()
        .identifier(identifier)
        .tenantIdentifier(tenantIdentifier)
        .userIdentifier(userIdentifier)
        .provider(provider)
        .accountAlias(accountAlias)
        .federatedUserId(federatedUserId)
        .federatedUsername(federatedUsername)
        .scope(scope)
        .encryptedAccessToken(encryptedAccessToken)
        .encryptedRefreshToken(encryptedRefreshToken)
        .encryptionKeyId(encryptionKeyId)
        .accessTokenExpiresAt(accessTokenExpiresAt)
        .refreshTokenExpiresAt(refreshTokenExpiresAt)
        .metadata(metadata)
        .createdAt(createdAt)
        .updatedAt(updatedAt);
  }

  public static class Builder {

    LinkedExternalAccountIdentifier identifier;
    TenantIdentifier tenantIdentifier;
    UserIdentifier userIdentifier;
    ExternalIdpProvider provider;
    AccountAlias accountAlias;
    String federatedUserId;
    String federatedUsername;
    String scope;
    EncryptedData encryptedAccessToken;
    EncryptedData encryptedRefreshToken;
    String encryptionKeyId;
    LocalDateTime accessTokenExpiresAt;
    LocalDateTime refreshTokenExpiresAt;
    Map<String, Object> metadata;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public Builder identifier(LinkedExternalAccountIdentifier identifier) {
      this.identifier = identifier;
      return this;
    }

    public Builder tenantIdentifier(TenantIdentifier tenantIdentifier) {
      this.tenantIdentifier = tenantIdentifier;
      return this;
    }

    public Builder userIdentifier(UserIdentifier userIdentifier) {
      this.userIdentifier = userIdentifier;
      return this;
    }

    public Builder provider(ExternalIdpProvider provider) {
      this.provider = provider;
      return this;
    }

    public Builder accountAlias(AccountAlias accountAlias) {
      this.accountAlias = accountAlias;
      return this;
    }

    public Builder federatedUserId(String federatedUserId) {
      this.federatedUserId = federatedUserId;
      return this;
    }

    public Builder federatedUsername(String federatedUsername) {
      this.federatedUsername = federatedUsername;
      return this;
    }

    public Builder scope(String scope) {
      this.scope = scope;
      return this;
    }

    public Builder encryptedAccessToken(EncryptedData encryptedAccessToken) {
      this.encryptedAccessToken = encryptedAccessToken;
      return this;
    }

    public Builder encryptedRefreshToken(EncryptedData encryptedRefreshToken) {
      this.encryptedRefreshToken = encryptedRefreshToken;
      return this;
    }

    public Builder encryptionKeyId(String encryptionKeyId) {
      this.encryptionKeyId = encryptionKeyId;
      return this;
    }

    public Builder accessTokenExpiresAt(LocalDateTime accessTokenExpiresAt) {
      this.accessTokenExpiresAt = accessTokenExpiresAt;
      return this;
    }

    public Builder refreshTokenExpiresAt(LocalDateTime refreshTokenExpiresAt) {
      this.refreshTokenExpiresAt = refreshTokenExpiresAt;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder createdAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedAt(LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public LinkedExternalAccount build() {
      return new LinkedExternalAccount(this);
    }
  }
}
