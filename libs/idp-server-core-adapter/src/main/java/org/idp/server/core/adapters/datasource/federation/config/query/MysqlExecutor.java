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

package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationQueries;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements FederationConfigurationSqlExecutor {

  String selectSql =
      """
            SELECT id, type, sso_provider, payload
             FROM federation_configurations \n
          """;

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, FederationType federationType, SsoProvider ssoProvider) {
    return selectOne(tenant, federationType, ssoProvider, false);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant,
      FederationType federationType,
      SsoProvider ssoProvider,
      boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                    WHERE tenant_id = ?
                    AND type = ?
                    AND sso_provider= ?"""
            + (includeDisabled ? "" : "\n                    AND enabled = true")
            + "\n                    ";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(federationType.name());
    params.add(ssoProvider.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, FederationConfigurationIdentifier identifier) {
    return selectOne(tenant, identifier, false);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, FederationConfigurationIdentifier identifier, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                    WHERE tenant_id = ?
                    AND id = ?"""
            + (includeDisabled ? "" : "\n                    AND enabled = true")
            + "\n                    ";
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant, FederationQueries queries) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String selectSql =
        """
            SELECT COUNT(*) as count FROM federation_configurations
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

    if (queries.hasSsoProvider()) {
      sql.append(" AND sso_provider = ?");
      params.add(queries.ssoProvider());
    }

    if (queries.hasDetails()) {
      for (Map.Entry<String, String> entry : queries.details().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sql.append(" AND payload ->> ? = ?");
        params.add(key);
        params.add(value);
      }
    }

    return sqlExecutor.selectOne(sql.toString(), params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, FederationQueries queries) {
    return selectList(tenant, queries, false);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, FederationQueries queries, boolean includeDisabled) {

    SqlExecutor sqlExecutor = new SqlExecutor();

    StringBuilder sql = new StringBuilder(selectSql).append("\nWHERE tenant_id = ?");

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    if (!includeDisabled) {
      sql.append("\n  AND enabled = true");
    }

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
    if (queries.hasSsoProvider()) {
      sql.append("\n  AND sso_provider = ?");
      params.add(queries.ssoProvider());
    }

    if (queries.hasDetails()) {
      for (Map.Entry<String, String> e : queries.details().entrySet()) {
        sql.append("\n  AND payload ->> ? = ?");
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
