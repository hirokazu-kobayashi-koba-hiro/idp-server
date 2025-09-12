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

package org.idp.server.core.adapters.datasource.identity.permission.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;
import org.idp.server.core.openid.identity.permission.PermissionQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements PermissionSqlExecutor {

  String selectSql = """
            SELECT id, name, description FROM permission
            """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, PermissionIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE id = ?::uuid
                 AND tenant_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByName(Tenant tenant, String name) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE name = ?
                 AND tenant_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(name);
    params.add(tenant.identifier().valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, PermissionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlBuilder =
        new StringBuilder(
            """
                SELECT count(*) as count FROM permission
                 WHERE tenant_id = ?::uuid
                """);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      sqlBuilder.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sqlBuilder.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?::uuid");
      params.add(queries.idAsUuid());
    }

    if (queries.hasName()) {
      sqlBuilder.append(" AND LOWER(name) LIKE ?");
      params.add("%" + queries.name().toLowerCase() + "%");
    }

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public List<Map<String, String>> selectAll(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = selectSql + " WHERE tenant_id = ?::uuid";

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, PermissionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlBuilder = new StringBuilder(selectSql + " WHERE tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      sqlBuilder.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sqlBuilder.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?::uuid");
      params.add(queries.idAsUuid());
    }

    if (queries.hasName()) {
      sqlBuilder.append(" AND LOWER(name) LIKE ?");
      params.add("%" + queries.name().toLowerCase() + "%");
    }

    sqlBuilder.append(" ORDER BY created_at DESC");
    sqlBuilder.append(" LIMIT ? OFFSET ?;");
    params.add(queries.limit());
    params.add(queries.offset());

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectList(sql, params);
  }
}
