package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.datasource.SqlExecutor;

public class MysqlExecutor implements ServerConfigSqlExecutor {

  JsonConverter jsonConverter;

  public MysqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    INSERT INTO authorization_server_configuration (tenant_id, token_issuer, payload)
                    VALUES (?, ?, ?);
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
                        SET payload = ?,
                        token_issuer = ?
                        WHERE tenant_id = ?;
                        """;
    String payload = jsonConverter.write(authorizationServerConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(payload);
    params.add(authorizationServerConfiguration.tokenIssuer().value());
    params.add(tenant.identifierValue());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
