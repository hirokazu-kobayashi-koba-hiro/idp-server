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

package org.idp.server.core.adapters.datasource.federation.config.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements FederationConfigurationSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, FederationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO federation_configurations (
            id,
            tenant_id,
            type,
            sso_provider,
            payload,
            enabled
            ) VALUES (
            ?::uuid,
            ?::uuid,
            ?,
            ?,
            ?::jsonb,
            ?
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());
    params.add(configuration.type().name());
    params.add(configuration.ssoProvider().name());
    params.add(jsonConverter.write(configuration.payload()));
    params.add(configuration.isEnabled());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, FederationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            UPDATE federation_configurations
            SET payload = ?::jsonb,
            sso_provider = ?,
            enabled = ?
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration.payload()));
    params.add(configuration.ssoProvider().name());
    params.add(configuration.isEnabled());
    params.add(configuration.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, FederationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM federation_configurations
            WHERE id = ?::uuid
            AND tenant_id = ?::uuid;
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().valueAsUuid());
    params.add(tenant.identifier().valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
