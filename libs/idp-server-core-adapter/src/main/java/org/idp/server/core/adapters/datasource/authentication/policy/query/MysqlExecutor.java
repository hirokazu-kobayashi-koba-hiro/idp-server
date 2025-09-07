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

package org.idp.server.core.adapters.datasource.authentication.policy.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements AuthenticationPolicyConfigurationSqlExecutor {

  String selectSql =
      """
               SELECT id, flow, payload
                FROM authentication_policy \n
              """;

  @Override
  public Map<String, String> selectOne(Tenant tenant, AuthFlow authFlow) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            WHERE tenant_id = ?
            AND flow = ?
            AND enabled = true
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(authFlow.name());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, AuthenticationPolicyConfigurationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND id = ?
                AND enabled = true
                """;

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
                FROM authentication_policy
                WHERE tenant_id = ?
            """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                limit ?
                offset ?
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(limit);
    params.add(offset);

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
