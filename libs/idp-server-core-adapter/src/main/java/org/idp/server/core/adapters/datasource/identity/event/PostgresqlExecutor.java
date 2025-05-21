package org.idp.server.core.adapters.datasource.identity.event;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventResult;
import org.idp.server.core.oidc.identity.event.UserLifecycleType;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements UserLifecycleEventResultSqlExecutor {

  JsonConverter converter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(
      Tenant tenant,
      UserLifecycleEvent userLifecycleEvent,
      List<UserLifecycleEventResult> results) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    StringBuilder sql =
        new StringBuilder(
            """
                INSERT INTO idp_user_lifecycle_event_result
                (
                id,
                tenant_id,
                user_id,
                lifecycle_type,
                executor_name,
                payload,
                status
                )
                VALUES
                """);

    User user = userLifecycleEvent.user();
    UserLifecycleType userLifecycleType = userLifecycleEvent.lifecycleType();
    List<Object> params = new ArrayList<>();
    for (int i = 0; i < results.size(); i++) {
      UserLifecycleEventResult result = results.get(i);
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("(?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb, ?)");
      params.add(result.identifier().value());
      params.add(tenant.identifierValue());
      params.add(user.sub());
      params.add(result.executorName());
      params.add(userLifecycleType.name());
      params.add(converter.write(result.data()));
      params.add(result.status().name());
    }
    sql.append(";");

    sqlExecutor.execute(sql.toString(), params);
  }
}
