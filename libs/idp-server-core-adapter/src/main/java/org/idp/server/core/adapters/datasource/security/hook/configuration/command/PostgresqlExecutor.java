/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.platform.datasource.SqlExecutor;
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
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());
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
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());

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
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
