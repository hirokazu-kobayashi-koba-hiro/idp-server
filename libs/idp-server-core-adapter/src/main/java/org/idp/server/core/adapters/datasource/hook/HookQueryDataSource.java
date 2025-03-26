package org.idp.server.core.adapters.datasource.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.hook.HookConfiguration;
import org.idp.server.core.hook.HookConfigurations;
import org.idp.server.core.hook.HookQueryRepository;
import org.idp.server.core.hook.HookTriggerType;
import org.idp.server.core.tenant.Tenant;

public class HookQueryDataSource implements HookQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  String selectSql =
      """
            SELECT id, trigger, payload FROM hook_configuration \n
            """;

  @Override
  public HookConfigurations find(Tenant tenant, HookTriggerType triggerType) {
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
    params.add(triggerType.name());

    List<Map<String, String>> results = sqlExecutor.selectList(sqlTemplate, params);

    if (results == null || results.isEmpty()) {
      return new HookConfigurations();
    }

    List<HookConfiguration> list =
        results.stream()
            .map(result -> jsonConverter.read(result.get("payload"), HookConfiguration.class))
            .toList();

    return new HookConfigurations(list);
  }
}
