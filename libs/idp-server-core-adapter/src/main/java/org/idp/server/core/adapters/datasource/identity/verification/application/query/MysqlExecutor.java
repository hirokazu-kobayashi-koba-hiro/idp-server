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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements IdentityVerificationApplicationQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE id = ?
                 AND tenant_id = ?
                 AND user_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifier().value());
    params.add(user.sub());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE id = ?
                 AND tenant_id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifier().value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByDetail(Tenant tenant, String key, String identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE JSON_EXTRACT(application_details, CONCAT('$.', ?)) = ?
                 AND tenant_id = ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(key);
    params.add(identifier);
    params.add(tenant.identifier().value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                     WHERE user_id = ?
                     AND tenant_id = ?
                     ORDER BY requested_at DESC;
                    """;

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifier().value());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(selectSql);
    sqlBuilder.append(" WHERE user_id = ? AND tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifier().value());

    if (queries.hasFrom()) {
      sqlBuilder.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sqlBuilder.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasType()) {
      sqlBuilder.append(" AND verification_type = ?");
      params.add(queries.type());
    }

    if (queries.hasClientId()) {
      sqlBuilder.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasStatus()) {
      sqlBuilder.append(" AND status = ?");
      params.add(queries.status());
    }

    if (queries.hasApplicationDetails()) {
      for (Map.Entry<String, String> entry : queries.applicationDetails().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sqlBuilder.append(" AND JSON_EXTRACT(application_details, CONCAT('$.', ?)) = ?");
        params.add(key);
        params.add(value);
      }
    }

    sqlBuilder.append(" ORDER BY created_at DESC");
    sqlBuilder.append(" LIMIT ? OFFSET ?;");
    params.add(queries.limit());
    params.add(queries.offset());

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectList(sql, params);
  }

  @Override
  public Map<String, String> selectCount(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append("SELECT COUNT(*) as count FROM identity_verification_application");
    sqlBuilder.append(" WHERE user_id = ? AND tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifier().value());

    if (queries.hasFrom()) {
      sqlBuilder.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sqlBuilder.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sqlBuilder.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasType()) {
      sqlBuilder.append(" AND verification_type = ?");
      params.add(queries.type());
    }

    if (queries.hasClientId()) {
      sqlBuilder.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasStatus()) {
      sqlBuilder.append(" AND status = ?");
      params.add(queries.status());
    }

    if (queries.hasApplicationDetails()) {
      for (Map.Entry<String, String> entry : queries.applicationDetails().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sqlBuilder.append(" AND JSON_EXTRACT(application_details, CONCAT('$.', ?)) = ?");
        params.add(key);
        params.add(value);
      }
    }

    String sql = sqlBuilder.toString();
    return sqlExecutor.selectOne(sql, params);
  }

  String selectSql =
      """
          SELECT id,
                 tenant_id,
                 client_id,
                 user_id,
                 verification_type,
                 application_details,
                 processes,
                 attributes,
                 status,
                 requested_at
          FROM identity_verification_application
          """;
}
