package org.idp.server.core.adapters.datasource.authentication.config.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PostgresqlExecutor implements AuthenticationConfigCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, AuthenticationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            INSERT INTO authentication_configuration (
            id,
            tenant_id,
            type,
            payload
            )
            VALUES (
            ?::uuid,
            ?::uuid,
            ?,
            ?::jsonb
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.id());
    params.add(tenant.identifierValue());
    params.add(configuration.type());
    params.add(jsonConverter.write(configuration.payload()));

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, AuthenticationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                UPDATE authentication_configuration
                SET payload = ?::jsonb
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(jsonConverter.write(configuration.payload()));
    params.add(configuration.id());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void delete(Tenant tenant, AuthenticationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                DELETE authentication_configuration
                WHERE id = ?::uuid
                AND tenant_id = ?::uuid
                """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.id());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
