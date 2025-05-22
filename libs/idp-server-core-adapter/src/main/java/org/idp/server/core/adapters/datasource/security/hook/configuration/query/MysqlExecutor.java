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


package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfigurationIdentifier;

public class MysqlExecutor implements SecurityEventHookConfigSqlExecutor {

  String selectSql =
      """
                SELECT id, type, payload, execution_order, enabled
                FROM security_event_hook_configurations \n
                """;

  @Override
  public List<Map<String, String>> selectListBy(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND enabled = true
                ORDER BY execution_order;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid
                ORDER BY execution_order;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                ORDER BY execution_order
                LIMIT ? OFFSET ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
