package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.SqlExecutor;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class MysqlExecutor implements ServerConfigSqlExecutor {

  JsonConverter jsonConverter;

  public MysqlExecutor() {
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void insert(AuthorizationServerConfiguration authorizationServerConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    INSERT INTO authorization_server_configuration (tenant_id, token_issuer, payload)
                    VALUES (?, ?, ?);
                    """;
    String payload = jsonConverter.write(authorizationServerConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(authorizationServerConfiguration.tenantId());
    params.add(authorizationServerConfiguration.tokenIssuer().value());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    SELECT tenant_id, token_issuer, payload
                    FROM authorization_server_configuration
                    WHERE tenant_id = ?;
                    """;
    return sqlExecutor.selectOne(sqlTemplate, List.of(tenantIdentifier.value()));
  }
}
