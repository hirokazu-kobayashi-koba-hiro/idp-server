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

package org.idp.server.core.adapters.datasource.oidc.clientinstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueries;
import org.idp.server.core.openid.clientinstance.ClientInstanceThumbprint;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements ClientInstanceSqlExecutor {

  JsonConverter jsonConverter;

  public PostgresqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  String selectColumns =
      """
      SELECT id, tenant_id, client_id, instance_key, status, attestation_evidence,
             device_id, user_id, created_at, updated_at, expires_at, revoked_at,
             revocation_reason
      FROM client_instance
      """;

  @Override
  public void insert(Tenant tenant, ClientInstance clientInstance) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO client_instance
        (id, tenant_id, client_id, instance_key, instance_key_thumbprint, status, attestation_evidence,
         device_id, user_id, expires_at)
        VALUES (?::uuid, ?::uuid, ?, ?::jsonb, ?, ?, ?::jsonb, ?::uuid, ?::uuid, ?)
        """;

    List<Object> params = new ArrayList<>();
    params.add(clientInstance.id());
    params.add(tenant.identifierUUID());
    params.add(clientInstance.clientId());
    params.add(jsonConverter.write(clientInstance.instanceKey()));
    params.add(clientInstance.instanceKeyThumbprint().value());
    params.add(clientInstance.status().name());
    params.add(jsonConverter.write(clientInstance.attestationEvidence()));
    params.add(clientInstance.deviceId());
    params.add(clientInstance.userId());
    params.add(clientInstance.expiresAt());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, ClientInstance clientInstance) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        UPDATE client_instance
        SET instance_key = ?::jsonb,
            status = ?,
            attestation_evidence = ?::jsonb,
            device_id = ?::uuid,
            expires_at = ?,
            revoked_at = ?,
            revocation_reason = ?,
            updated_at = now()
        WHERE tenant_id = ?::uuid
        AND client_id = ?
        AND id = ?::uuid
        """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(clientInstance.instanceKey()));
    params.add(clientInstance.status().name());
    params.add(jsonConverter.write(clientInstance.attestationEvidence()));
    params.add(clientInstance.deviceId());
    params.add(clientInstance.expiresAt());
    params.add(clientInstance.revokedAt());
    params.add(
        clientInstance.revocationReason().exists()
            ? clientInstance.revocationReason().name()
            : null);
    params.add(tenant.identifierUUID());
    params.add(clientInstance.clientId());
    params.add(clientInstance.id());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(
      Tenant tenant, RequestedClientId requestedClientId, ClientInstanceIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        DELETE FROM client_instance
        WHERE tenant_id = ?::uuid
        AND client_id = ?
        AND id = ?::uuid
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());
    params.add(identifier.value());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, RequestedClientId requestedClientId, ClientInstanceIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND client_id = ?
            AND id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, ClientInstanceIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, ClientInstanceQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    List<Object> params = new ArrayList<>();
    StringBuilder sql = new StringBuilder(selectColumns);
    appendConditions(sql, params, tenant, queries);
    sql.append(" ORDER BY created_at DESC");
    sql.append(" LIMIT ? OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, ClientInstanceQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    List<Object> params = new ArrayList<>();
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS count FROM client_instance");
    appendConditions(sql, params, tenant, queries);

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  private void appendConditions(
      StringBuilder sql, List<Object> params, Tenant tenant, ClientInstanceQueries queries) {
    sql.append(" WHERE tenant_id = ?::uuid");
    params.add(tenant.identifierUUID());

    if (queries.hasClientId()) {
      sql.append(" AND client_id = ?");
      params.add(queries.clientId());
    }
    if (queries.hasUserId()) {
      sql.append(" AND user_id = ?::uuid");
      params.add(queries.userId());
    }
    if (queries.hasStatus()) {
      sql.append(" AND status = ?");
      params.add(queries.status().name());
    }
    if (queries.hasRevocationReason()) {
      sql.append(" AND revocation_reason = ?");
      params.add(queries.revocationReason().name());
    }
    if (queries.hasPlatform()) {
      sql.append(" AND attestation_evidence->>'platform' = ?");
      params.add(queries.platform());
    }
    if (queries.hasCertificateSerial()) {
      sql.append(" AND attestation_evidence @> ?::jsonb");
      params.add(
          jsonConverter.write(
              Map.of(
                  "chain",
                  Map.of("certificates", List.of(Map.of("serial", queries.certificateSerial()))))));
    }
    if (queries.hasInstanceKeyThumbprint()) {
      sql.append(" AND instance_key_thumbprint = ?");
      params.add(queries.instanceKeyThumbprint());
    }
    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }
    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }
  }

  @Override
  public Map<String, String> selectOneByThumbprint(
      Tenant tenant, ClientInstanceThumbprint thumbprint) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND instance_key_thumbprint = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(thumbprint.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectActiveListByUser(
      Tenant tenant, RequestedClientId requestedClientId, String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND client_id = ?
            AND user_id = ?::uuid
            AND status = 'active'
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());
    params.add(userId);

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectListByUser(Tenant tenant, String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND user_id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(userId);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
