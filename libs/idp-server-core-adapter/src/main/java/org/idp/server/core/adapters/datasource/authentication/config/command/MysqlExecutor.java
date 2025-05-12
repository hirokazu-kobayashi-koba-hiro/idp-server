package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

import java.util.ArrayList;
import java.util.List;


public class MysqlExecutor implements AuthenticationConfigCommandSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, AuthenticationConfiguration configuration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate = """
            INSERT INTO authentication_configuration (
            id,
            tenant_id,
            type,
            payload
            )
            VALUES (
            ?,
            ?,
            ?,
            ?
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(configuration.id());
    params.add(tenant.identifierValue());
    params.add(configuration.type());
    params.add(jsonConverter.write(configuration));

    sqlExecutor.execute(sqlTemplate, params);
  }
}
