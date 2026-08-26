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
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.crypto.EncryptedData;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * PostgreSQL persistence for {@code linked_external_accounts}.
 *
 * <p>Command and query sides share one class here because this is a spike. The MySQL executor split
 * that the rest of the adapters follow comes with the MySQL implementation.
 */
public class LinkedExternalAccountDataSource
    implements LinkedExternalAccountCommandRepository, LinkedExternalAccountQueryRepository {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static final String SELECT_COLUMNS =
      """
      SELECT id, tenant_id, user_id, provider, account_alias, federated_user_id,
             federated_username, scope, encrypted_access_token, encrypted_refresh_token,
             encryption_key_id, access_token_expires_at, refresh_token_expires_at,
             metadata, created_at, updated_at
        FROM linked_external_accounts
      """;

  @Override
  public void register(Tenant tenant, LinkedExternalAccount account) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO linked_external_accounts (
        id, tenant_id, user_id, provider, account_alias, federated_user_id,
        federated_username, scope, encrypted_access_token, encrypted_refresh_token,
        encryption_key_id, access_token_expires_at, refresh_token_expires_at, metadata
        )
        VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::jsonb);
        """;

    List<Object> params = new ArrayList<>();
    params.add(account.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(account.userIdentifier().valueAsUuid());
    params.add(account.provider().value());
    params.add(account.accountAlias().value());
    params.add(account.federatedUserId());
    params.add(account.federatedUsername());
    params.add(account.scope());
    params.add(jsonConverter.write(account.encryptedAccessToken()));
    params.add(toJsonOrNull(account.encryptedRefreshToken()));
    params.add(account.encryptionKeyId());
    params.add(account.accessTokenExpiresAt());
    params.add(account.refreshTokenExpiresAt());
    params.add(account.metadata() == null ? null : jsonConverter.write(account.metadata()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, LinkedExternalAccount account) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        UPDATE linked_external_accounts
           SET encrypted_access_token = ?::jsonb,
               encrypted_refresh_token = ?::jsonb,
               encryption_key_id = ?,
               access_token_expires_at = ?,
               refresh_token_expires_at = ?,
               scope = ?,
               federated_username = ?,
               updated_at = now()
         WHERE id = ?::uuid AND tenant_id = ?::uuid;
        """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(account.encryptedAccessToken()));
    params.add(toJsonOrNull(account.encryptedRefreshToken()));
    params.add(account.encryptionKeyId());
    params.add(account.accessTokenExpiresAt());
    params.add(account.refreshTokenExpiresAt());
    params.add(account.scope());
    params.add(account.federatedUsername());
    params.add(account.identifier().valueAsUuid());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, LinkedExternalAccount account) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM linked_external_accounts WHERE id = ?::uuid AND tenant_id = ?::uuid;
        """;

    sqlExecutor.execute(
        sqlTemplate, List.of(account.identifier().valueAsUuid(), tenant.identifierUUID()));
  }

  @Override
  public void deleteAll(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM linked_external_accounts WHERE user_id = ?::uuid AND tenant_id = ?::uuid;
        """;

    sqlExecutor.execute(
        sqlTemplate, List.of(userIdentifier.valueAsUuid(), tenant.identifierUUID()));
  }

  @Override
  public List<LinkedExternalAccount> findList(Tenant tenant, UserIdentifier userIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ?::uuid AND user_id = ?::uuid
             ORDER BY created_at;
            """;

    List<Map<String, String>> results =
        sqlExecutor.selectList(
            sqlTemplate, List.of(tenant.identifierUUID(), userIdentifier.valueAsUuid()));

    return results.stream().map(this::convert).toList();
  }

  @Override
  public LinkedExternalAccount find(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ?::uuid AND user_id = ?::uuid AND account_alias = ?;
            """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(tenant.identifierUUID(), userIdentifier.valueAsUuid(), alias.value()));

    return result == null || result.isEmpty() ? new LinkedExternalAccount() : convert(result);
  }

  @Override
  public LinkedExternalAccount findByUserAndFederatedUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ?::uuid
               AND user_id = ?::uuid
               AND provider = ?
               AND federated_user_id = ?;
            """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(
                tenant.identifierUUID(),
                userIdentifier.valueAsUuid(),
                provider.value(),
                federatedUserId));

    return result == null || result.isEmpty() ? new LinkedExternalAccount() : convert(result);
  }

  @Override
  public boolean existsForOtherUser(
      Tenant tenant,
      UserIdentifier userIdentifier,
      ExternalIdpProvider provider,
      String federatedUserId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT count(*) AS count
          FROM linked_external_accounts
         WHERE tenant_id = ?::uuid
           AND provider = ?
           AND federated_user_id = ?
           AND user_id <> ?::uuid;
        """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(
                tenant.identifierUUID(),
                provider.value(),
                federatedUserId,
                userIdentifier.valueAsUuid()));

    return result != null && !result.isEmpty() && Integer.parseInt(result.get("count")) > 0;
  }

  @Override
  public int countByProvider(
      Tenant tenant, UserIdentifier userIdentifier, ExternalIdpProvider provider) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT count(*) AS count
          FROM linked_external_accounts
         WHERE tenant_id = ?::uuid AND user_id = ?::uuid AND provider = ?;
        """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(tenant.identifierUUID(), userIdentifier.valueAsUuid(), provider.value()));

    if (result == null || result.isEmpty()) {
      return 0;
    }
    return Integer.parseInt(result.get("count"));
  }

  @Override
  public LinkedExternalAccount lock(
      Tenant tenant, UserIdentifier userIdentifier, AccountAlias alias) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        SELECT_COLUMNS
            + """
             WHERE tenant_id = ?::uuid AND user_id = ?::uuid AND account_alias = ?
             FOR UPDATE;
            """;

    Map<String, String> result =
        sqlExecutor.selectOne(
            sqlTemplate,
            List.of(tenant.identifierUUID(), userIdentifier.valueAsUuid(), alias.value()));

    return result == null || result.isEmpty() ? new LinkedExternalAccount() : convert(result);
  }

  private LinkedExternalAccount convert(Map<String, String> row) {
    LinkedExternalAccount.Builder builder =
        new LinkedExternalAccount.Builder()
            .identifier(new LinkedExternalAccountIdentifier(row.get("id")))
            .tenantIdentifier(new TenantIdentifier(row.get("tenant_id")))
            .userIdentifier(new UserIdentifier(row.get("user_id")))
            .provider(new ExternalIdpProvider(row.get("provider")))
            .accountAlias(new AccountAlias(row.get("account_alias")))
            .federatedUserId(row.get("federated_user_id"))
            .federatedUsername(row.get("federated_username"))
            .scope(row.get("scope"))
            .encryptedAccessToken(toEncryptedData(row.get("encrypted_access_token")))
            .encryptedRefreshToken(toEncryptedData(row.get("encrypted_refresh_token")))
            .encryptionKeyId(row.get("encryption_key_id"))
            .accessTokenExpiresAt(toDateTime(row.get("access_token_expires_at")))
            .refreshTokenExpiresAt(toDateTime(row.get("refresh_token_expires_at")))
            .createdAt(toDateTime(row.get("created_at")))
            .updatedAt(toDateTime(row.get("updated_at")));

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
