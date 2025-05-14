package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;

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
