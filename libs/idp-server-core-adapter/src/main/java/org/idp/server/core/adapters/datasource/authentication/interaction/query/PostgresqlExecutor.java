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

package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationInteractionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            SELECT
            authentication_transaction_id,
            payload
            FROM authentication_interactions
            WHERE authentication_transaction_id = ?::uuid
            AND tenant_id = ?::uuid
            AND interaction_type = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(identifier.valueAsUuid());
    params.add(tenant.identifierUUID());
    params.add(type);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, AuthenticationInteractionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM authentication_interactions");
    sql.append(" WHERE tenant_id = ?::uuid");
    sql.append(" AND created_at BETWEEN ? AND ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    if (queries.hasType()) {
      sql.append(" AND interaction_type = ?");
      params.add(queries.type());
    }

    sql.append(" LIMIT ? OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, AuthenticationInteractionQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
            authentication_transaction_id,
            interaction_type,
            payload
            FROM authentication_interactions
            """);
    sql.append(" WHERE tenant_id = ?::uuid");
    sql.append(" AND created_at BETWEEN ? AND ?");
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(queries.from());
    params.add(queries.to());

    if (queries.hasType()) {
      sql.append(" AND interaction_type = ?");
      params.add(queries.type());
    }

    sql.append(" LIMIT ? OFFSET ?");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sql.toString(), params);
  }
}
