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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.*;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * PostgreSQL persistence for {@code account_linking_sessions}.
 *
 * <p>Command and query sides share one class here because this is a spike. The MySQL executor split
 * that the rest of the adapters follow comes with the MySQL implementation.
 */
public class AccountLinkingSessionDataSource
    implements AccountLinkingSessionCommandRepository, AccountLinkingSessionQueryRepository {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void register(Tenant tenant, AccountLinkingSession session) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO account_linking_sessions (
        state, tenant_id, user_id, client_id, provider, account_alias,
        redirect_uri, requested_scope, code_verifier, nonce, status, expires_at
        )
        VALUES (?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

    List<Object> params = new ArrayList<>();
    params.add(session.state().value());
    params.add(tenant.identifierUUID());
    params.add(session.userIdentifier().valueAsUuid());
    params.add(session.requestedClientId().value());
    params.add(session.provider().value());
    params.add(session.hasAccountAlias() ? session.accountAlias().value() : null);
    params.add(session.redirectUri());
    params.add(session.requestedScope());
    params.add(session.codeVerifier());
    params.add(session.nonce());
    params.add(session.status().name().toLowerCase());
    params.add(session.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public boolean claim(
      Tenant tenant,
      AccountLinkingState state,
      AccountLinkingSessionStatus from,
      AccountLinkingSessionStatus to,
      LocalDateTime now) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        UPDATE account_linking_sessions
           SET status = ?, updated_at = now()
         WHERE state = ?
           AND tenant_id = ?::uuid
           AND status = ?
           AND expires_at > ?;
        """;

    List<Object> params = new ArrayList<>();
    params.add(to.name().toLowerCase());
    params.add(state.value());
    params.add(tenant.identifierUUID());
    params.add(from.name().toLowerCase());
    params.add(now);

    return sqlExecutor.executeAndReturnAffectedRows(sqlTemplate, params) == 1;
  }

  @Override
  public void update(Tenant tenant, AccountLinkingSession session) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        UPDATE account_linking_sessions
           SET account_alias = ?,
               acr = ?,
               amr = ?,
               authenticated_at = ?,
               federated_user_id = ?,
               federated_username = ?,
               granted_scope = ?,
               encrypted_access_token = ?::jsonb,
               encrypted_refresh_token = ?::jsonb,
               encryption_key_id = ?,
               access_token_expires_at = ?,
               refresh_token_expires_at = ?,
               updated_at = now()
         WHERE state = ? AND tenant_id = ?::uuid;
        """;

    ParkedCredentials credentials = session.parkedCredentials();
    List<Object> params = new ArrayList<>();
    params.add(session.hasAccountAlias() ? session.accountAlias().value() : null);
    params.add(session.acr());
    params.add(session.amr());
    params.add(session.authenticatedAt());
    params.add(credentials == null ? null : credentials.federatedUserId());
    params.add(credentials == null ? null : credentials.federatedUsername());
    params.add(credentials == null ? null : credentials.grantedScope());
    params.add(toJsonOrNull(credentials == null ? null : credentials.encryptedAccessToken()));
    params.add(toJsonOrNull(credentials == null ? null : credentials.encryptedRefreshToken()));
    params.add(credentials == null ? null : credentials.encryptionKeyId());
    params.add(credentials == null ? null : credentials.accessTokenExpiresAt());
    params.add(credentials == null ? null : credentials.refreshTokenExpiresAt());
    params.add(session.state().value());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AccountLinkingState state) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM account_linking_sessions WHERE state = ? AND tenant_id = ?::uuid;
        """;

    sqlExecutor.execute(sqlTemplate, List.of(state.value(), tenant.identifierUUID()));
  }

  @Override
  public AccountLinkingSession find(Tenant tenant, AccountLinkingState state) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT state, tenant_id, user_id, client_id, provider, account_alias,
               redirect_uri, requested_scope, code_verifier, nonce, acr, amr,
               authenticated_at, status, federated_user_id, federated_username,
               granted_scope, encrypted_access_token, encrypted_refresh_token,
               encryption_key_id, access_token_expires_at, refresh_token_expires_at,
               expires_at
          FROM account_linking_sessions
         WHERE state = ? AND tenant_id = ?::uuid;
        """;

    Map<String, String> result =
        sqlExecutor.selectOne(sqlTemplate, List.of(state.value(), tenant.identifierUUID()));

    if (result == null || result.isEmpty()) {
      return new AccountLinkingSession();
    }

    return convert(result);
  }

  private AccountLinkingSession convert(Map<String, String> row) {
    AccountLinkingSession.Builder builder =
        new AccountLinkingSession.Builder()
            .state(new AccountLinkingState(row.get("state")))
            .tenantIdentifier(new TenantIdentifier(row.get("tenant_id")))
            .userIdentifier(new UserIdentifier(row.get("user_id")))
            .requestedClientId(new RequestedClientId(row.get("client_id")))
            .provider(new ExternalIdpProvider(row.get("provider")))
            .redirectUri(row.get("redirect_uri"))
            .requestedScope(row.get("requested_scope"))
            .codeVerifier(row.get("code_verifier"))
            .nonce(row.get("nonce"))
            .acr(row.get("acr"))
            .amr(row.get("amr"))
            .authenticatedAt(toDateTime(row.get("authenticated_at")))
            .status(AccountLinkingSessionStatus.valueOf(row.get("status").toUpperCase()))
            .expiresAt(toDateTime(row.get("expires_at")));

    if (row.get("account_alias") != null) {
      builder.accountAlias(new AccountAlias(row.get("account_alias")));
    }

    if (row.get("encrypted_access_token") != null) {
      builder.parkedCredentials(
          new ParkedCredentials(
              row.get("federated_user_id"),
              row.get("federated_username"),
              row.get("granted_scope"),
              toEncryptedData(row.get("encrypted_access_token")),
              toEncryptedData(row.get("encrypted_refresh_token")),
              row.get("encryption_key_id"),
              toDateTime(row.get("access_token_expires_at")),
              toDateTime(row.get("refresh_token_expires_at"))));
    }

    return builder.build();
  }

  private String toJsonOrNull(EncryptedData data) {
    return data == null ? null : jsonConverter.write(data);
  }

  private EncryptedData toEncryptedData(String json) {
    if (json == null || json.isEmpty()) {
      return null;
    }
    return jsonConverter.read(json, EncryptedData.class);
  }

  private LocalDateTime toDateTime(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return LocalDateTime.parse(value.replace(" ", "T"));
  }
}
