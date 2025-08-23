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

package org.idp.server.core.adapters.datasource.identity.role.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.role.RoleIdentifier;
import org.idp.server.core.openid.identity.role.RoleQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements RoleSqlExecutor {

  String selectSql =
      """
              SELECT role.id, role.name, role.description, role.created_at,
                 COALESCE(
                               JSON_AGG(
                                   JSON_BUILD_OBJECT('permission_id', permission.id, 'permission_name', permission.name, 'permission_description', permission.description)
                               ) FILTER (WHERE permission.id IS NOT NULL),
                               '[]'
                           ) AS permissions
             FROM role
                      LEFT JOIN role_permission ON role_id = role.id
                      LEFT JOIN permission ON permission.id = role_permission.permission_id
             %s
             GROUP BY role.id, role.name, role.description, role.created_at
            """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, RoleIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        String.format(
            selectSql,
            """
                 WHERE role.id = ?::uuid
                 AND role.tenant_id = ?::uuid
                """);

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByName(Tenant tenant, String name) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        String.format(
            selectSql,
            """
                 WHERE role.name = ?
                 AND role.tenant_id = ?::uuid
                """);

    List<Object> params = new ArrayList<>();
    params.add(name);
    params.add(tenant.identifier().valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, RoleQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sqlBuilder =
        new StringBuilder(
            """
                SELECT count(*) FROM role
                WHERE role.tenant_id = ?::uuid
                """);

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      sqlBuilder.append(" AND role.created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sqlBuilder.append(" AND role.created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND role.id = ?::uuid");
      params.add(queries.idAsUuid());
    }

    if (queries.hasName()) {
      sqlBuilder.append(" AND LOWER(role.name) LIKE ?");
      params.add("%" + queries.name().toLowerCase() + "%");
    }

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectOne(sql, params);
  }

  @Override
  public List<Map<String, String>> selectAll(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = String.format(selectSql, " WHERE role.tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, RoleQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder where = new StringBuilder(" WHERE role.tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasFrom()) {
      where.append(" AND role.created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      where.append(" AND role.created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      where.append(" AND role.id = ?::uuid");
      params.add(queries.idAsUuid());
    }

    if (queries.hasName()) {
      where.append(" AND LOWER(role.name) LIKE ?");
      params.add("%" + queries.name().toLowerCase() + "%");
    }

    String whereString = where.toString();

    StringBuilder sqlBuilder = new StringBuilder(String.format(selectSql, whereString));
    sqlBuilder.append(" ORDER BY role.created_at DESC");
    sqlBuilder.append(" LIMIT ? OFFSET ?;");

    params.add(queries.limit());
    params.add(queries.offset());

    String sql = sqlBuilder.toString();

    return sqlExecutor.selectList(sql, params);
  }
}
