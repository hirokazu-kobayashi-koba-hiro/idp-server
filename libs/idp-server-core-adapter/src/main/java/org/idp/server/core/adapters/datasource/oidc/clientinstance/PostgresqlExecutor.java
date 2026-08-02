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
             device_id, created_at, updated_at, expires_at, revoked_at
      FROM client_instance
      """;

  @Override
  public void insert(Tenant tenant, ClientInstance clientInstance) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        INSERT INTO client_instance
        (id, tenant_id, client_id, instance_key, status, attestation_evidence, device_id, expires_at)
        VALUES (?, ?::uuid, ?, ?::jsonb, ?, ?::jsonb, ?::uuid, ?)
        """;

    List<Object> params = new ArrayList<>();
    params.add(clientInstance.id());
    params.add(tenant.identifierUUID());
    params.add(clientInstance.clientId());
    params.add(jsonConverter.write(clientInstance.instanceKey()));
    params.add(clientInstance.status().name());
    params.add(jsonConverter.write(clientInstance.attestationEvidence()));
    params.add(clientInstance.deviceId());
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
            updated_at = now()
        WHERE tenant_id = ?::uuid
        AND client_id = ?
        AND id = ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(clientInstance.instanceKey()));
    params.add(clientInstance.status().name());
    params.add(jsonConverter.write(clientInstance.attestationEvidence()));
    params.add(clientInstance.deviceId());
    params.add(clientInstance.expiresAt());
    params.add(clientInstance.revokedAt());
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
        AND id = ?
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
            AND id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, RequestedClientId requestedClientId, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND client_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectActiveListByDevice(
      Tenant tenant, RequestedClientId requestedClientId, String deviceId) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectColumns
            + """
            WHERE tenant_id = ?::uuid
            AND client_id = ?
            AND device_id = ?::uuid
            AND status = 'active'
            AND revoked_at IS NULL
            AND (expires_at IS NULL OR expires_at > now())
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(requestedClientId.value());
    params.add(deviceId);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
