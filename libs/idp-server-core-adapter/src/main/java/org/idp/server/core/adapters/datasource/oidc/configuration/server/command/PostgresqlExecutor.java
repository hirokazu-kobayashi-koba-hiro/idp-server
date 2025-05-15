package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class PostgresqlExecutor implements ServerConfigSqlExecutor {

  JsonConverter jsonConverter;

  public PostgresqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    INSERT INTO authorization_server_configuration (
                    tenant_id,
                    token_issuer,
                    payload
                    )
                    VALUES (?::uuid, ?, ?::jsonb);
                    """;
    String payload = jsonConverter.write(authorizationServerConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(tenant.identifierValue());
    params.add(authorizationServerConfiguration.tokenIssuer().value());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
            """
                        UPDATE authorization_server_configuration
                        SET payload = ?::jsonb,
                        token_issuer = ?
                        WHERE tenant_id = ?::uuid;
                        """;
    String payload = jsonConverter.write(authorizationServerConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(payload);
    params.add(authorizationServerConfiguration.tokenIssuer().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
