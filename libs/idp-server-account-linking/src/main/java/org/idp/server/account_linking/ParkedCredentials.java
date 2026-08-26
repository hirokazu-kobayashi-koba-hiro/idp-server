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
import org.idp.server.platform.crypto.EncryptedData;

/**
 * Tokens obtained at the callback, encrypted and held against a linking session until claimed.
 *
 * <p>Held here rather than written straight into {@code linked_external_accounts} so that the
 * unauthenticated callback cannot finalize a link. Encryption matches {@code oauth_token}: AES-GCM
 * via {@code AesCipher}, serialized as {@code {cipherText, iv}}.
 *
 * <p>The identity fields are optional. A plain OAuth 2.0 delegation grants access to a resource
 * without saying who the resource owner is.
 */
public class ParkedCredentials {

  String federatedUserId;
  String federatedUsername;
  String grantedScope;
  EncryptedData encryptedAccessToken;
  EncryptedData encryptedRefreshToken;
  String encryptionKeyId;
  LocalDateTime accessTokenExpiresAt;
  LocalDateTime refreshTokenExpiresAt;

  public ParkedCredentials() {}

  ParkedCredentials(Builder builder) {
    this.federatedUserId = builder.federatedUserId;
    this.federatedUsername = builder.federatedUsername;
    this.grantedScope = builder.grantedScope;
    this.encryptedAccessToken = builder.encryptedAccessToken;
    this.encryptedRefreshToken = builder.encryptedRefreshToken;
    this.encryptionKeyId = builder.encryptionKeyId;
    this.accessTokenExpiresAt = builder.accessTokenExpiresAt;
    this.refreshTokenExpiresAt = builder.refreshTokenExpiresAt;
  }

  public String federatedUserId() {
    return federatedUserId;
  }

  public boolean hasFederatedUserId() {
    return federatedUserId != null && !federatedUserId.isEmpty();
  }

  public String federatedUsername() {
    return federatedUsername;
  }

  public String grantedScope() {
    return grantedScope;
  }

  public EncryptedData encryptedAccessToken() {
    return encryptedAccessToken;
  }

  public EncryptedData encryptedRefreshToken() {
    return encryptedRefreshToken;
  }

  public boolean hasRefreshToken() {
    return encryptedRefreshToken != null;
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

  public boolean exists() {
    return encryptedAccessToken != null;
  }

  public static class Builder {

    String federatedUserId;
    String federatedUsername;
    String grantedScope;
    EncryptedData encryptedAccessToken;
    EncryptedData encryptedRefreshToken;
    String encryptionKeyId;
    LocalDateTime accessTokenExpiresAt;
    LocalDateTime refreshTokenExpiresAt;

    public Builder federatedUserId(String federatedUserId) {
      this.federatedUserId = federatedUserId;
      return this;
    }

    public Builder federatedUsername(String federatedUsername) {
      this.federatedUsername = federatedUsername;
      return this;
    }

    public Builder grantedScope(String grantedScope) {
      this.grantedScope = grantedScope;
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

    public ParkedCredentials build() {
      return new ParkedCredentials(this);
    }
  }
}
