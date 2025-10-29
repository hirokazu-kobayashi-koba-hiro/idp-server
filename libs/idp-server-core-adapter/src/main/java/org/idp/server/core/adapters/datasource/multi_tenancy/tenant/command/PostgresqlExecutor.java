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

package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class PostgresqlExecutor implements TenantCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO tenant(
            id,
            name,
            type,
            domain,
            authorization_provider,
            attributes,
            ui_config,
            cors_config,
            session_config,
            security_event_log_config,
            security_event_user_config,
            identity_policy_config,
            main_organization_id,
            enabled
            )
            VALUES (
            ?::uuid,
            ?,
            ?,
            ?,
            ?,
            ?::jsonb,
            ?::jsonb,
            ?::jsonb,
            ?::jsonb,
            ?::jsonb,
            ?::jsonb,
            ?::jsonb,
            ?::uuid,
            ?
            )
            """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierUUID());
    params.add(tenant.name().value());
    params.add(tenant.type().name());
    params.add(tenant.domain().value());
    params.add(tenant.authorizationProvider().name());
    params.add(jsonConverter.write(tenant.attributesAsMap()));
    params.add(jsonConverter.write(tenant.uiConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.corsConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.sessionConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.securityEventLogConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.securityEventUserAttributeConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.identityPolicyConfig().toMap()));
    params.add(tenant.mainOrganizationIdentifier().valueAsUuid());
    params.add(tenant.isEnabled());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                UPDATE tenant
                SET name = ?,
                domain = ?,
                attributes = ?::jsonb,
                ui_config = ?::jsonb,
                cors_config = ?::jsonb,
                session_config = ?::jsonb,
                security_event_log_config = ?::jsonb,
                security_event_user_config = ?::jsonb,
                identity_policy_config = ?::jsonb,
                enabled = ?
                WHERE id = ?::uuid;
                """;
    List<Object> params = new ArrayList<>();
    params.add(tenant.name().value());
    params.add(tenant.domain().value());
    params.add(jsonConverter.write(tenant.attributesAsMap()));
    params.add(jsonConverter.write(tenant.uiConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.corsConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.sessionConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.securityEventLogConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.securityEventUserAttributeConfiguration().toMap()));
    params.add(jsonConverter.write(tenant.identityPolicyConfig().toMap()));
    params.add(tenant.isEnabled());
    params.add(tenant.identifierUUID());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                DELETE FROM tenant
                WHERE id = ?::uuid;
                """;
    List<Object> params = new ArrayList<>();
    params.add(tenantIdentifier.valueAsUuid());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
