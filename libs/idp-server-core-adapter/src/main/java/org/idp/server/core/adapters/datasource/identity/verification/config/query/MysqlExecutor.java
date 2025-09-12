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

package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements IdentityVerificationConfigSqlExecutor {

  String selectSql =
      """
          SELECT id, payload
          FROM identity_verification_configuration \n
          """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, IdentityVerificationType type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE tenant_id = ?
            AND type = ?
            AND enabled = true
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(type.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, IdentityVerificationConfigurationIdentifier identifier) {

    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND id = ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, IdentityVerificationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String selectSql =
        """
            SELECT COUNT(*) as count FROM identity_verification_configuration
            """;
    StringBuilder sql = new StringBuilder(selectSql).append(" WHERE tenant_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (queries.hasFrom()) {
      sql.append(" AND created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sql.append(" AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append(" AND id = ?");
      params.add(queries.id());
    }

    if (queries.hasType()) {
      sql.append(" AND type = ?");
      params.add(queries.type());
    }

    if (queries.hasDetails()) {
      for (Map.Entry<String, String> entry : queries.details().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND JSON_EXTRACT(payload, CONCAT('$.', ?)) = ?");
        params.add(key);
        params.add(value);
      }
    }

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, IdentityVerificationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder(selectSql).append("\nWHERE tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifier().value());

    if (queries.hasFrom()) {
      sql.append("\n  AND created_at >= ?");
      params.add(queries.from());
    }
    if (queries.hasTo()) {
      sql.append("\n  AND created_at <= ?");
      params.add(queries.to());
    }

    if (queries.hasId()) {
      sql.append("\n  AND id = ?");
      params.add(queries.id());
    }
    if (queries.hasType()) {
      sql.append("\n  AND type = ?");
      params.add(queries.type());
    }

    if (queries.hasDetails()) {
      for (Map.Entry<String, String> e : queries.details().entrySet()) {
        sql.append("\n  AND JSON_EXTRACT(payload, CONCAT('$.', ?)) = ?");
        params.add(e.getKey());
        params.add(e.getValue());
      }
    }

    sql.append("\nORDER BY created_at DESC, id DESC").append("\nLIMIT ?").append("\nOFFSET ?");

    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }
}
