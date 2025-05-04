package org.idp.server.core.adapters.datasource.security.hook;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.hook.SecurityEventHookResult;

public class PostgresqlExecutor implements SecurityEventHoolResultSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(
      Tenant tenant, SecurityEvent securityEvent, List<SecurityEventHookResult> results) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
      INSERT INTO security_event_hook_results
      (
       id,
       tenant_id,
       security_event_id,
       security_event_type,
       security_event_hook,
       security_event_payload,
       status
      )
      VALUES
  """);

    String eventPayload = converter.write(securityEvent.detail().toMap());

    List<Object> params = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      SecurityEventHookResult result = results.get(i);
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?, ?, ?, ?, ?, ?::jsonb, ?)");
      params.add(result.identifier().value());
      params.add(tenant.identifierValue());
      params.add(securityEvent.identifier().value());
      params.add(securityEvent.type().value());
      params.add(result.type().name());
      params.add(eventPayload);
      params.add(result.status().name());
    }
    sql.append(";");

    sqlExecutor.execute(sql.toString(), params);
  }
}
