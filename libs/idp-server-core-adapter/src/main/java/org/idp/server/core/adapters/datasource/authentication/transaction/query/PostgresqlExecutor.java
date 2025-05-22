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


package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationTransactionQuerySqlExecutor {

  @Override
  public Map<String, String> selectOne(Tenant tenant, AuthorizationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + " "
            + """
            WHERE authorization_id = ?::uuid
            AND tenant_id = ?::uuid
            """;
    List<Object> params = new ArrayList<>();
    params.add(identifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByDeviceId(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + " "
            + """
                WHERE authentication_device_id = ?::uuid
                AND tenant_id = ?::uuid
                ORDER BY created_at DESC
                limit 1
                """;
    List<Object> params = new ArrayList<>();
    params.add(authenticationDeviceIdentifier.value());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql =
      """
          SELECT
          authorization_id,
          tenant_id,
          authorization_flow,
          client_id,
          user_id,
          user_payload,
          context,
          authentication_device_id,
          authentication_policy,
          interactions,
          created_at,
          expired_at
          FROM authentication_transaction
          """;
}
