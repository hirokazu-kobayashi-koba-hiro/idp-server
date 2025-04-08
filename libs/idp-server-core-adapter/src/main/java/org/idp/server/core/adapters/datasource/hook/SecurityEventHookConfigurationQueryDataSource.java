package org.idp.server.core.adapters.datasource.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.security.event.SecurityEventType;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.hook.SecurityEventHookConfigurations;
import org.idp.server.core.tenant.Tenant;

public class SecurityEventHookConfigurationQueryDataSource
    implements SecurityEventHookConfigurationQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  String selectSql =
      """
            SELECT id, trigger, payload FROM hook_configuration \n
            """;

  @Override
  public SecurityEventHookConfigurations find(Tenant tenant, SecurityEventType triggerType) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND trigger = ?
                AND enabled = true
                ORDER BY execution_order;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(triggerType.value());

    List<Map<String, String>> results = sqlExecutor.selectList(sqlTemplate, params);

    if (results == null || results.isEmpty()) {
      return new SecurityEventHookConfigurations();
    }

    List<SecurityEventHookConfiguration> list =
        results.stream()
            .map(
                result ->
                    jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class))
            .toList();

    return new SecurityEventHookConfigurations(list);
  }
}
