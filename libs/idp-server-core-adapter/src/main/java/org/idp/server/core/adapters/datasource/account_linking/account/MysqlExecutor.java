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

package org.idp.server.core.adapters.datasource.account_linking.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.core.adapters.datasource.account_linking.ModelConverter;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements LinkedExternalAccountSqlExecutor {

  static final String SELECT_COLUMNS =
      """
      SELECT id, tenant_id, user_id, provider, account_alias, federated_user_id,
             federated_username, scope, encrypted_access_token, encrypted_refresh_token,
             encryption_key_id, access_token_expires_at, refresh_token_expires_at,
             metadata, created_at, updated_at
        FROM linked_external_accounts
      """;

  @Override
  public void insert(Tenant tenant, LinkedExternalAccount account) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO linked_external_accounts (
        id, tenant_id, user_id, provider, account_alias, federated_user_id,
        federated_username, scope, encrypted_access_token, encrypted_refresh_token,
        encryption_key_id, access_token_expires_at, refresh_token_expires_at, metadata
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

    List<Object> params = new ArrayList<>();
    params.add(account.identifier().value());
    params.add(tenant.identifierValue());
    params.add(account.userIdentifier().value());
    params.add(account.provider().value());
    params.add(account.accountAlias().value());
    params.add(account.federatedUserId());
    params.add(account.federatedUsername());
    params.add(account.scope());
    params.add(ModelConverter.toJsonOrNull(account.encryptedAccessToken()));
    params.add(ModelConverter.toJsonOrNull(account.encryptedRefreshToken()));
    params.add(account.encryptionKeyId());
    params.add(account.accessTokenExpiresAt());
    params.add(account.refreshTokenExpiresAt());
    params.add(
        account.metadata() == null
            ? null
            : JsonConverter.snakeCaseInstance().write(account.metadata()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, LinkedExternalAccount account) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        UPDATE linked_external_accounts
           SET encrypted_access_token = ?,
               encrypted_refresh_token = ?,
               encryption_key_id = ?,
               access_token_expires_at = ?,
               refresh_token_expires_at = ?,
               scope = ?,
               federated_username = ?,
               updated_at = now()
         WHERE id = ? AND tenant_id = ?;
        """;

    List<Object> params = new ArrayList<>();
    params.add(ModelConverter.toJsonOrNull(account.encryptedAccessToken()));
    params.add(ModelConverter.toJsonOrNull(account.encryptedRefreshToken()));
    params.add(account.encryptionKeyId());
    params.add(account.accessTokenExpiresAt());
    params.add(account.refreshTokenExpiresAt());
    params.add(account.scope());
    params.add(account.federatedUsername());
    params.add(account.identifier().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, LinkedExternalAccount account) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM linked_external_accounts WHERE id = ? AND tenant_id = ?;
        """;

    sqlExecutor.execute(
        sqlTemplate, List.of(account.identifier().value(), tenant.identifierValue()));
  }

  @Override
  public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM linked_external_accounts WHERE user_id = ? AND tenant_id = ?;
        """;

    sqlExecutor.execute(sqlTemplate, List.of(userIdentifier.value(), tenant.identifierValue()));
  }

  @Override
  public List<Map<String, String>> selectListByUser(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ? AND user_id = ?
             ORDER BY created_at;
            """;

    return sqlExecutor.selectList(
        sqlTemplate, List.of(tenant.identifierValue(), userIdentifier.value()));
  }

  @Override
  public Map<String, String> selectByAlias(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ? AND user_id = ? AND account_alias = ?;
            """;

    return sqlExecutor.selectOne(
        sqlTemplate, List.of(tenant.identifierValue(), userIdentifier.value(), alias.value()));
  }

  @Override
  public Map<String, String> selectByUserAndFederatedUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ?
               AND user_id = ?
               AND provider = ?
               AND federated_user_id = ?;
            """;

    return sqlExecutor.selectOne(
        sqlTemplate,
        List.of(
            tenant.identifierValue(), userIdentifier.value(), provider.value(), federatedUserId));
  }

  @Override
  public int countByOtherUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT count(*) AS count
          FROM linked_external_accounts
         WHERE tenant_id = ?
           AND provider = ?
           AND federated_user_id = ?
           AND user_id <> ?;
        """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(
                tenant.identifierValue(),
                provider.value(),
                federatedUserId,
                userIdentifier.value()));

    return toCount(result);
  }

  @Override
  public int countByProvider(
      Tenant tenant, UserIdentifier userIdentifier, ExternalIdpProvider provider) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT count(*) AS count
          FROM linked_external_accounts
         WHERE tenant_id = ? AND user_id = ? AND provider = ?;
        """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(tenant.identifierValue(), userIdentifier.value(), provider.value()));

    return toCount(result);
  }

  @Override
  public Map<String, String> selectByAliasForUpdate(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ? AND user_id = ? AND account_alias = ?
             FOR UPDATE;
            """;

    return sqlExecutor.selectOne(
        sqlTemplate, List.of(tenant.identifierValue(), userIdentifier.value(), alias.value()));
  }

  private int toCount(Map<String, String> result) {
    if (result == null || result.isEmpty()) {
      return 0;
    }
    return Integer.parseInt(result.get("count"));
  }
}
