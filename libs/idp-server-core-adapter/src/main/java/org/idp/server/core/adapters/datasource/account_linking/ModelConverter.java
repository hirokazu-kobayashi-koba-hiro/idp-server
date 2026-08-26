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

package org.idp.server.core.adapters.datasource.account_linking;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.account_linking.*;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class ModelConverter {

  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public static AccountLinkingSession convertSession(Map<String, String> result) {
    AccountLinkingSession.Builder builder =
        new AccountLinkingSession.Builder()
            .state(new AccountLinkingState(result.get("state")))
            .tenantIdentifier(new TenantIdentifier(result.get("tenant_id")))
            .userIdentifier(new UserIdentifier(result.get("user_id")))
            .requestedClientId(new RequestedClientId(result.get("client_id")))
            .provider(new ExternalIdpProvider(result.get("provider")))
            .redirectUri(result.get("redirect_uri"))
            .requestedScope(result.get("requested_scope"))
            .codeVerifier(result.get("code_verifier"))
            .nonce(result.get("nonce"))
            .acr(result.get("acr"))
            .amr(result.get("amr"))
            .authenticatedAt(toDateTime(result.get("authenticated_at")))
            .status(AccountLinkingSessionStatus.valueOf(result.get("status").toUpperCase()))
            .browserBindingHash(result.get("browser_binding_hash"))
            .expiresAt(toDateTime(result.get("expires_at")));

    if (result.get("account_alias") != null) {
      builder.accountAlias(new AccountAlias(result.get("account_alias")));
    }

    if (result.get("encrypted_access_token") != null) {
      builder.parkedCredentials(
          new ParkedCredentials(
              result.get("federated_user_id"),
              result.get("federated_username"),
              result.get("granted_scope"),
              toEncryptedData(result.get("encrypted_access_token")),
              toEncryptedData(result.get("encrypted_refresh_token")),
              result.get("encryption_key_id"),
              toDateTime(result.get("access_token_expires_at")),
              toDateTime(result.get("refresh_token_expires_at"))));
    }

    return builder.build();
  }

  public static LinkedExternalAccount convertAccount(Map<String, String> result) {
    return new LinkedExternalAccount.Builder()
        .identifier(new LinkedExternalAccountIdentifier(result.get("id")))
        .tenantIdentifier(new TenantIdentifier(result.get("tenant_id")))
        .userIdentifier(new UserIdentifier(result.get("user_id")))
        .provider(new ExternalIdpProvider(result.get("provider")))
        .accountAlias(new AccountAlias(result.get("account_alias")))
        .federatedUserId(result.get("federated_user_id"))
        .federatedUsername(result.get("federated_username"))
        .scope(result.get("scope"))
        .encryptedAccessToken(toEncryptedData(result.get("encrypted_access_token")))
        .encryptedRefreshToken(toEncryptedData(result.get("encrypted_refresh_token")))
        .encryptionKeyId(result.get("encryption_key_id"))
        .accessTokenExpiresAt(toDateTime(result.get("access_token_expires_at")))
        .refreshTokenExpiresAt(toDateTime(result.get("refresh_token_expires_at")))
        .createdAt(toDateTime(result.get("created_at")))
        .updatedAt(toDateTime(result.get("updated_at")))
        .build();
  }

  public static String toJsonOrNull(EncryptedData data) {
    return data == null ? null : jsonConverter.write(data);
  }

  public static EncryptedData toEncryptedData(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    return jsonConverter.read(json, EncryptedData.class);
  }

  public static LocalDateTime toDateTime(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalDateTime.parse(value.replace(" ", "T"));
  }
}
