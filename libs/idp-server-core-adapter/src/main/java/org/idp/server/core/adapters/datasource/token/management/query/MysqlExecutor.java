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

package org.idp.server.core.adapters.datasource.token.management.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.OAuthTokenQueries;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements OAuthTokenManagementQuerySqlExecutor {

  @Override
  public Map<String, String> selectOneById(Tenant tenant, OAuthTokenIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            FROM oauth_token
            WHERE tenant_id = ?
            AND id = ?;
            """;
    List<Object> params = List.of(tenant.identifierValue(), identifier.value());
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, OAuthTokenQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(selectSql);
    sqlBuilder.append(" FROM oauth_token");
    sqlBuilder.append(" WHERE tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    appendFilters(sqlBuilder, params, queries);

    sqlBuilder.append(" ORDER BY access_token_created_at DESC");
    sqlBuilder.append(" LIMIT ? OFFSET ?;");
    params.add(queries.limit());
    params.add(queries.offset());

    return sqlExecutor.selectList(sqlBuilder.toString(), params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, OAuthTokenQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder();
    sql.append("SELECT id FROM oauth_token");
    sql.append(" WHERE tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    appendFilters(sql, params, queries);

    String countSql = "SELECT COUNT(*) as count FROM (" + sql + " LIMIT 1000001) t";
    return sqlExecutor.selectOne(countSql, params);
  }

  @Override
  public Map<String, String> selectCountByUser(Tenant tenant, String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sql =
        """
            SELECT COUNT(*) as count FROM oauth_token
            WHERE tenant_id = ?
            AND user_id = ?;
            """;
    List<Object> params = List.of(tenant.identifierValue(), userId);
    return sqlExecutor.selectOne(sql, params);
  }

  private void appendFilters(
      StringBuilder sqlBuilder, List<Object> params, OAuthTokenQueries queries) {

    if (!queries.includeExpired()) {
      sqlBuilder.append(" AND access_token_expires_at > now()");
    }

    if (queries.hasUserId()) {
      sqlBuilder.append(" AND user_id = ?");
      params.add(queries.userId());
    }

    if (queries.hasClientId()) {
      sqlBuilder.append(" AND client_id = ?");
      params.add(queries.clientId());
    }

    if (queries.hasGrantType()) {
      sqlBuilder.append(" AND grant_type = ?");
      params.add(queries.grantType());
    }

    if (queries.hasFrom()) {
      sqlBuilder.append(" AND access_token_created_at >= ?");
      params.add(queries.from());
    }

    if (queries.hasTo()) {
      sqlBuilder.append(" AND access_token_created_at <= ?");
      params.add(queries.to());
    }
  }

  String selectSql =
      """
          SELECT
          id,
          tenant_id,
          token_issuer,
          token_type,
          user_id,
          client_id,
          grant_type,
          scopes,
          expires_in,
          access_token_expires_at,
          access_token_created_at,
          refresh_token_expires_at,
          refresh_token_created_at,
          hashed_refresh_token,
          client_certification_thumbprint
          """;
}
