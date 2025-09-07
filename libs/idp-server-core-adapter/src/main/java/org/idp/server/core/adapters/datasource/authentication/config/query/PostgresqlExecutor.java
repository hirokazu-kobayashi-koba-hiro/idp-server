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

package org.idp.server.core.adapters.datasource.authentication.config.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationConfigSqlExecutor {

  String selectSql =
      """
           SELECT id, type, payload
            FROM authentication_configuration \n
          """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, String type) {
    return selectOne(tenant, type, false);
  }

  @Override
  public Map<String, String> selectOne(Tenant tenant, String type, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE tenant_id = ?::uuid
            AND type = ?"""
            + (includeDisabled ? "" : "\n            AND enabled = true")
            + "\n            ";

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(type);

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
    return selectOne(tenant, identifier, false);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid"""
            + (includeDisabled ? "" : "\n                AND enabled = true")
            + "\n                ";

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(identifier.valueAsUuid());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectCount(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                SELECT COUNT(id)
                FROM authentication_configuration
                WHERE tenant_id = ?::uuid
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    return selectList(tenant, limit, offset, false);
  }

  @Override
  public List<Map<String, String>> selectList(
      Tenant tenant, int limit, int offset, boolean includeDisabled) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid"""
            + (includeDisabled ? "" : "\n                AND enabled = true")
            + "\n                limit ?\n                offset ?\n                ";

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
