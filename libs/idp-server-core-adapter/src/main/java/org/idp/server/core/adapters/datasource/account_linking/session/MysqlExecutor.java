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

package org.idp.server.core.adapters.datasource.account_linking.session;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.AccountLinkingSessionStatus;
import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.account_linking.ParkedCredentials;
import org.idp.server.core.adapters.datasource.account_linking.ModelConverter;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AccountLinkingSessionSqlExecutor {

  @Override
  public void insert(Tenant tenant, AccountLinkingSession session) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO account_linking_sessions (
        state, tenant_id, user_id, client_id, provider, account_alias,
        redirect_uri, requested_scope, code_verifier, nonce, status, expires_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

    List<Object> params = new ArrayList<>();
    params.add(session.state().value());
    params.add(tenant.identifierValue());
    params.add(session.userIdentifier().value());
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
  public int updateStatus(
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
           AND tenant_id = ?
           AND status = ?
           AND expires_at > ?;
        """;

    List<Object> params = new ArrayList<>();
    params.add(to.name().toLowerCase());
    params.add(state.value());
    params.add(tenant.identifierValue());
    params.add(from.name().toLowerCase());
    params.add(now);

    return sqlExecutor.executeAndReturnAffectedRows(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, AccountLinkingSession session) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        UPDATE account_linking_sessions
           SET account_alias = ?,
               browser_binding_hash = ?,
               acr = ?,
               amr = ?,
               authenticated_at = ?,
               federated_user_id = ?,
               federated_username = ?,
               granted_scope = ?,
               encrypted_access_token = ?,
               encrypted_refresh_token = ?,
               encryption_key_id = ?,
               access_token_expires_at = ?,
               refresh_token_expires_at = ?,
               updated_at = now()
         WHERE state = ? AND tenant_id = ?;
        """;

    ParkedCredentials credentials = session.parkedCredentials();
    List<Object> params = new ArrayList<>();
    params.add(session.hasAccountAlias() ? session.accountAlias().value() : null);
    params.add(session.browserBindingHash());
    params.add(session.acr());
    params.add(session.amr());
    params.add(session.authenticatedAt());
    params.add(credentials == null ? null : credentials.federatedUserId());
    params.add(credentials == null ? null : credentials.federatedUsername());
    params.add(credentials == null ? null : credentials.grantedScope());
    params.add(
        ModelConverter.toJsonOrNull(
            credentials == null ? null : credentials.encryptedAccessToken()));
    params.add(
        ModelConverter.toJsonOrNull(
            credentials == null ? null : credentials.encryptedRefreshToken()));
    params.add(credentials == null ? null : credentials.encryptionKeyId());
    params.add(credentials == null ? null : credentials.accessTokenExpiresAt());
    params.add(credentials == null ? null : credentials.refreshTokenExpiresAt());
    params.add(session.state().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AccountLinkingState state) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM account_linking_sessions WHERE state = ? AND tenant_id = ?;
        """;

    sqlExecutor.execute(sqlTemplate, List.of(state.value(), tenant.identifierValue()));
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, AccountLinkingState state) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT state, tenant_id, user_id, client_id, provider, account_alias,
               redirect_uri, requested_scope, code_verifier, nonce, acr, amr,
               authenticated_at, status, browser_binding_hash, federated_user_id,
               federated_username, granted_scope, encrypted_access_token,
               encrypted_refresh_token, encryption_key_id, access_token_expires_at,
               refresh_token_expires_at, expires_at
          FROM account_linking_sessions
         WHERE state = ? AND tenant_id = ?;
        """;

    return sqlExecutor.selectOne(sqlTemplate, List.of(state.value(), tenant.identifierValue()));
  }
}
