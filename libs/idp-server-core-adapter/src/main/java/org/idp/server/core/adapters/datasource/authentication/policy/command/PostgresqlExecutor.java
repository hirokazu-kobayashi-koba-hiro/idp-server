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

package org.idp.server.core.adapters.datasource.authentication.policy.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationPolicyConfigurationSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, AuthenticationPolicyConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO authentication_policy (
            id,
            tenant_id,
            flow,
            payload
            )
            VALUES (
            ?::uuid,
            ?::uuid,
            ?,
            ?::jsonb
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.idAsUUID());
    params.add(tenant.identifierUUID());
    params.add(configuration.flow());
    params.add(jsonConverter.write(configuration.toMap()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, AuthenticationPolicyConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                UPDATE authentication_policy
                SET payload = ?::jsonb
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration.toMap()));
    params.add(configuration.idAsUUID());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationPolicyConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                DELETE authentication_policy
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.idAsUUID());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
