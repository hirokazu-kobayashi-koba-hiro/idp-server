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
package org.idp.server.core.adapters.datasource.authentication.risk.configuration.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.idp.server.core.openid.authentication.risk.RiskAssessmentConfig;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements RiskAssessmentConfigCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, RiskAssessmentConfig configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
        INSERT INTO risk_assessment_configuration (
        id,
        tenant_id,
        payload,
        enabled,
        created_at,
        updated_at
        )
        VALUES (
        ?::uuid,
        ?::uuid,
        ?::jsonb,
        ?,
        NOW(),
        NOW()
        )
        ON CONFLICT (tenant_id)
        DO UPDATE SET
        payload = EXCLUDED.payload,
        enabled = EXCLUDED.enabled,
        updated_at = NOW();
        """;

    List<Object> params = new ArrayList<>();
    params.add(UUID.randomUUID());
    params.add(tenant.identifierUUID());
    params.add(jsonConverter.write(configuration.toMap()));
    params.add(configuration.isEnabled());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, RiskAssessmentConfig configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
        UPDATE risk_assessment_configuration
        SET payload = ?::jsonb,
            enabled = ?,
            updated_at = NOW()
        WHERE tenant_id = ?::uuid;
        """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration.toMap()));
    params.add(configuration.isEnabled());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
        DELETE FROM risk_assessment_configuration
        WHERE tenant_id = ?::uuid;
        """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
