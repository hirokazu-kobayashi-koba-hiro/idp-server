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
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.delegation.ExternalIdentityVerificationApplicationIdentifier;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements IdentityVerificationApplicationQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE id = ?::uuid
                 AND tenant_id = ?::uuid
                 AND user_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(user.subAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, ExternalIdentityVerificationApplicationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                 WHERE external_application_id = ?
                 AND tenant_id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + " "
            + """
                     WHERE user_id = ?::uuid
                     AND tenant_id = ?::uuid
                     ORDER BY requested_at DESC;
                    """;

    List<Object> params = new ArrayList<>();
    params.add(user.subAsUuid());
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(selectSql);
    sqlBuilder.append(" WHERE user_id = ?::uuid AND tenant_id = ?::uuid");

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
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
    if (queries.hasType()) {
      sqlBuilder.append(" AND verification_type = ?");
      params.add(queries.type());
    }
    if (queries.hasClientId()) {
      sqlBuilder.append(" AND client_id = ?");
      params.add(queries.clientId());
    }
    if (queries.hasExternalApplicationId()) {
      sqlBuilder.append(" AND external_application_id = ?");
      params.add(queries.externalApplicationId());
    }
    if (queries.hasExternalService()) {
      sqlBuilder.append(" AND external_service = ?");
      params.add(queries.externalService());
    }

    if (queries.hasStatus()) {
      sqlBuilder.append(" AND status = ?");
      params.add(queries.status());
    }

    if (queries.hasApplicationDetails()) {
      for (Map.Entry<String, String> entry : queries.applicationDetails().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sqlBuilder.append(" AND application_details ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    if (queries.hasExternalApplicationDetails()) {
      for (Map.Entry<String, String> entry : queries.externalApplicationDetails().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sqlBuilder.append(" AND external_application_details ->> ? = ?");
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

  String selectSql =
      """
          SELECT id,
                       tenant_id,
                       client_id,
                       user_id,
                       verification_type,
                       application_details,
                       external_service,
                       external_application_id,
                       external_application_details,
                       examination_results,
                       processes,
                       status,
                       requested_at,
                       examination_results,
                       comment
                 FROM identity_verification_application
          """;
}
