package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationIdentifier;

public class PostgresqlExecutor implements SecurityEventHookConfigSqlExecutor {

  String selectSql =
      """
                SELECT id, type, payload, execution_order, enabled
                FROM security_event_hook_configurations \n
                """;

  @Override
  public List<Map<String, String>> selectListBy(Tenant tenant) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND enabled = true
                ORDER BY execution_order;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    return sqlExecutor.selectList(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                AND id = ?::uuid;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(identifier.value());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public List<Map<String, String>> selectList(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        selectSql
            + """
                WHERE tenant_id = ?::uuid
                ORDER BY execution_order
                LIMIT ? OFFSET ?;
                """;

    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());

    return sqlExecutor.selectList(sqlTemplate, params);
  }
}
