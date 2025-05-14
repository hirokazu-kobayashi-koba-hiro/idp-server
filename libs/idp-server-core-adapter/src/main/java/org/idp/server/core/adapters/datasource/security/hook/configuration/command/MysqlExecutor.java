package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.adapters.datasource.security.event.SecurityEventSqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEvents;
import org.idp.server.core.security.event.SecurityEventSearchCriteria;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;

import java.util.ArrayList;
import java.util.List;

public class MysqlExecutor implements SecurityEventHookConfigSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, SecurityEventHookConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
            """
                    INSERT INTO security_event_hook_configurations (
                    id,
                    tenant_id,
                    payload,
                    execution_order
                    )
                    VALUES (
                    ?::uuid,
                    ?::uuid,
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
                    UPDATE security_event_hook_configurations (
                    id,
                    tenant_id,
                    SET payload = ?::jsonb,
                    execution_order = ?
                    WHERE id = ?::uuid;
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
                    WHERE id = ?::uuid;
                    AND tenant_id = ?::uuid;
                    """;
    List<Object> params = new ArrayList<>();
    params.add(configuration.identifier().value());
    params.add(tenant.identifier().value());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
