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
import org.idp.server.platform.datasource.SqlDuplicateKeyException;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements TenantDataKeySqlExecutor {

  static final String SELECT_COLUMNS =
      """
      SELECT id, tenant_id, purpose, version, status, wrapped_key, kek_id, created_at
        FROM tenant_data_keys
      """;

  @Override
  public Map<String, String> selectActive(Tenant tenant, String purpose) {
    String sql = SELECT_COLUMNS + " WHERE tenant_id = ? AND purpose = ? AND status = 'active';";
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierValue(), purpose));
  }

  /**
   * A locking read. Under REPEATABLE READ a plain SELECT returns the snapshot taken at the
   * transaction's first read, which predates the row the winner committed.
   */
  @Override
  public Map<String, String> selectActiveCommitted(Tenant tenant, String purpose) {
    String sql =
        SELECT_COLUMNS + " WHERE tenant_id = ? AND purpose = ? AND status = 'active' FOR SHARE;";
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierValue(), purpose));
  }

  /**
   * A locking read, for the same reason as {@link #selectActiveCommitted}: a stored token read
   * under its row lock may name a data key created after this transaction's snapshot.
   */
  @Override
  public Map<String, String> selectOne(Tenant tenant, String id) {
    String sql = SELECT_COLUMNS + " WHERE tenant_id = ? AND id = ? FOR SHARE;";
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierValue(), id));
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant) {
    String sql = SELECT_COLUMNS + " WHERE tenant_id = ? ORDER BY purpose, version DESC;";
    return new SqlExecutor().selectList(sql, List.of(tenant.identifierValue()));
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
      conditions.add("tenant_id = ?");
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
         WHERE tenant_id = ? AND purpose = ?;
        """;
    return new SqlExecutor().selectOne(sql, List.of(tenant.identifierValue(), purpose));
  }

  /**
   * A duplicate leaves a MySQL transaction usable, unlike PostgreSQL, so the conflict is caught
   * rather than avoided. INSERT IGNORE would also swallow unrelated errors.
   */
  @Override
  public boolean insertIfAbsent(Tenant tenant, TenantDataKey key) {
    String sql =
        """
        INSERT INTO tenant_data_keys (id, tenant_id, purpose, version, status, wrapped_key, kek_id)
        VALUES (?, ?, ?, ?, ?, ?, ?);
        """;
    List<Object> params = new ArrayList<>();
    params.add(key.id());
    params.add(tenant.identifierValue());
    params.add(key.purpose());
    params.add(key.version());
    params.add(key.status().value());
    params.add(ModelConverter.toJson(key.wrappedKey()));
    params.add(key.kekId());
    try {
      new SqlExecutor().execute(sql, params);
      return true;
    } catch (SqlDuplicateKeyException exception) {
      return false;
    }
  }

  @Override
  public void updateStatus(Tenant tenant, String id, DataKeyStatus status) {
    String sql =
        """
        UPDATE tenant_data_keys
           SET status = ?, updated_at = CURRENT_TIMESTAMP(6)
         WHERE tenant_id = ? AND id = ?;
        """;
    new SqlExecutor().execute(sql, List.of(status.value(), tenant.identifierValue(), id));
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
           SET wrapped_key = ?, kek_id = ?, updated_at = CURRENT_TIMESTAMP(6)
         WHERE tenant_id = ? AND id = ?;
        """;
    new SqlExecutor()
        .execute(
            sql,
            List.of(
                ModelConverter.toJson(key.wrappedKey()),
                key.kekId(),
                key.tenantIdentifier().value(),
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
