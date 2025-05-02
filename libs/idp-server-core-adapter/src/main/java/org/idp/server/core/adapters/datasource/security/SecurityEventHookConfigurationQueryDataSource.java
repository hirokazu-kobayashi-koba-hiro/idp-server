package org.idp.server.core.adapters.datasource.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.hook.SecurityEventHookConfigurations;

public class SecurityEventHookConfigurationQueryDataSource
    implements SecurityEventHookConfigurationQueryRepository {

  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  String selectSql =
      """
            SELECT id, payload FROM security_event_hook_configuration
            """;

  @Override
  public SecurityEventHookConfigurations find(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?
                AND enabled = true
                ORDER BY execution_order;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

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
