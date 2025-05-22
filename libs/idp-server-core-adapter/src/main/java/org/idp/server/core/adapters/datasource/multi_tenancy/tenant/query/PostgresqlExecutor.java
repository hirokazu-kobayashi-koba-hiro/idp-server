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


package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.*;

public class PostgresqlExecutor implements TenantQuerySqlExecutor {

  String selectSql =
      """
              SELECT
                id,
                name,
                type,
                domain,
                authorization_provider,
                database_type,
                attributes
                FROM tenant
              """;

  @Override
  public Map<String, String> selectOne(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = selectSql + " " + """
            WHERE id = ?::uuid
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectAdmin() {

    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate = selectSql + " " + """
            WHERE type = ?
            """;
    List<Object> params = new ArrayList<>();
    params.add("ADMIN");

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(List<TenantIdentifier> tenantIdentifiers) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sqlTemplateBuilder =
        new StringBuilder(selectSql + " " + """
            WHERE id IN (
            """);

    List<Object> params = new ArrayList<>();
    for (TenantIdentifier tenantIdentifier : tenantIdentifiers) {

      if (!params.isEmpty()) {
        sqlTemplateBuilder.append(", ");
      }
      sqlTemplateBuilder.append("?::uuid");
      params.add(tenantIdentifier.value());
    }

    sqlTemplateBuilder.append(" )");
    String sqlTemplate = sqlTemplateBuilder.toString();

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
