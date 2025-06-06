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

package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.hook.SecurityEventHookConfiguration;

public class PostgresqlExecutor implements SecurityEventHookConfigSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    INSERT INTO security_event_hook_configurations (
                    id,
                    tenant_id,
                    type,
                    payload,
                    execution_order
                    )
                    VALUES (
                    ?::uuid,
                    ?::uuid,
                    ?,
                    ?::jsonb,
                    ?
                    ) ON CONFLICT DO NOTHING;
                    """;
    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());
    params.add(configuration.hookType().name());
    params.add(jsonConverter.write(configuration));
    params.add(configuration.executionOrder());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    UPDATE security_event_hook_configurations
                    SET payload = ?::jsonb,
                    execution_order = ?
                    WHERE id = ?::uuid
                    AND tenant_id = ?::uuid;
                    """;
    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration));
    params.add(configuration.executionOrder());
    params.add(configuration.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    DELETE FROM security_event_hook_configurations
                    WHERE id = ?::uuid
                    AND tenant_id = ?::uuid;
                    """;
    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
