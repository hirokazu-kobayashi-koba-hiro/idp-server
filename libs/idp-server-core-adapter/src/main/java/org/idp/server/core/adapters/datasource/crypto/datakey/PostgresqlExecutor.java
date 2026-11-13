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
package org.idp.server.core.adapters.datasource.crypto.datakey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.idp.server.platform.crypto.datakey.DataKeyStatus;
import org.idp.server.platform.crypto.datakey.TenantDataKey;
import org.idp.server.platform.crypto.datakey.TenantDataKeyQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements TenantDataKeySqlExecutor {

  static final String SELECT_COLUMNS =
      """
      SELECT id, tenant_id, purpose, version, status, wrapped_key, kek_id, created_at
        FROM tenant_data_keys
      """;

  @Override
  public Map<String, String> selectActive(Tenant tenant, String purpose) {
    String sql =
        SELECT_COLUMNS + " WHERE tenant_id = ?::uuid AND purpose = ? AND status = 'active';";
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierUUID(), purpose));
  }

  /** READ COMMITTED already sees the row the winner committed. */
  @Override
  public Map<String, String> selectActiveCommitted(Tenant tenant, String purpose) {
    return selectActive(tenant, purpose);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, String id) {
    String sql = SELECT_COLUMNS + " WHERE tenant_id = ?::uuid AND id = ?::uuid;";
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierUUID(), id));
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant) {
    String sql = SELECT_COLUMNS + " WHERE tenant_id = ?::uuid ORDER BY purpose, version DESC;";
    return new SqlExecutor().selectList(sql, List.of(tenant.identifierUUID()));
  }

  @Override
  public List<Map<String, String>> selectList(TenantDataKeyQueries queries) {
    List<Object> params = new ArrayList<>();
    String sql =
        SELECT_COLUMNS
            + where(queries, params)
            + " ORDER BY tenant_id, purpose, version DESC LIMIT ? OFFSET ?;";
    params.add(queries.limit());
    params.add(queries.offset());
    return new SqlExecutor().selectList(sql, params);
  }

  @Override
  public Map<String, String> selectCount(TenantDataKeyQueries queries) {
    List<Object> params = new ArrayList<>();
    String sql = "SELECT COUNT(*) AS count FROM tenant_data_keys" + where(queries, params) + ";";
    return new SqlExecutor().selectOne(sql, params);
  }

  static String where(TenantDataKeyQueries queries, List<Object> params) {
    List<String> conditions = new ArrayList<>();
    if (queries.hasTenantId()) {
      conditions.add("tenant_id = ?::uuid");
      params.add(queries.tenantId());
    }
    if (queries.hasKekId()) {
      conditions.add("kek_id = ?");
      params.add(queries.kekId());
    }
    if (queries.hasStatus()) {
      conditions.add("status = ?");
      params.add(queries.status());
    }
    if (queries.hasPurpose()) {
      conditions.add("purpose = ?");
      params.add(queries.purpose());
    }
    return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
  }

  @Override
  public Map<String, String> selectLatestVersion(Tenant tenant, String purpose) {
    String sql =
        """
        SELECT COALESCE(MAX(version), 0) AS version
          FROM tenant_data_keys
         WHERE tenant_id = ?::uuid AND purpose = ?;
        """;
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierUUID(), purpose));
  }

  @Override
  public boolean insertIfAbsent(Tenant tenant, TenantDataKey key) {
    String sql =
        """
        INSERT INTO tenant_data_keys (id, tenant_id, purpose, version, status, wrapped_key, kek_id)
        VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?)
        ON CONFLICT DO NOTHING
        RETURNING id;
        """;
    List<Object> params = new ArrayList<>();
    params.add(key.id());
    params.add(tenant.identifierUUID());
    params.add(key.purpose());
    params.add(key.version());
    params.add(key.status().value());
    params.add(ModelConverter.toJson(key.wrappedKey()));
    params.add(key.kekId());
    return new SqlExecutor().executeAndCheckReturned(sql, params);
  }

  @Override
  public void updateStatus(Tenant tenant, String id, DataKeyStatus status) {
    String sql =
        """
        UPDATE tenant_data_keys
           SET status = ?, updated_at = now()
         WHERE tenant_id = ?::uuid AND id = ?::uuid;
        """;
    new SqlExecutor().execute(sql, List.of(status.value(), tenant.identifierUUID(), id));
  }

  @Override
  public List<Map<String, String>> selectWrappedBy(Set<String> kekIds, int limit) {
    String placeholders = String.join(", ", java.util.Collections.nCopies(kekIds.size(), "?"));
    String sql =
        SELECT_COLUMNS
            + " WHERE kek_id IN ("
            + placeholders
            + ") AND status <> 'destroyed' ORDER BY id LIMIT ?;";
    List<Object> params = new ArrayList<>(kekIds);
    params.add(limit);
    return new SqlExecutor().selectList(sql, params);
  }

  @Override
  public void updateWrapping(TenantDataKey key) {
    String sql =
        """
        UPDATE tenant_data_keys
           SET wrapped_key = ?::jsonb, kek_id = ?, updated_at = now()
         WHERE tenant_id = ?::uuid AND id = ?::uuid;
        """;
    new SqlExecutor()
        .execute(
            sql,
            List.of(
                ModelConverter.toJson(key.wrappedKey()),
                key.kekId(),
                key.tenantIdentifier().valueAsUuid(),
                key.id()));
  }

  @Override
  public List<Map<String, String>> countByKekId() {
    String sql =
        """
        SELECT kek_id, COUNT(*) AS data_keys
          FROM tenant_data_keys
         WHERE status <> 'destroyed'
         GROUP BY kek_id;
        """;
    return new SqlExecutor().selectList(sql, List.of());
  }
}
