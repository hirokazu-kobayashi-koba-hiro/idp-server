package org.idp.server.core.adapters.datasource.configuration.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.datasource.SqlExecutor;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.tenant.TenantIdentifier;

public class PostgresqlExecutor implements ServerConfigSqlExecutor {

  JsonConverter jsonConverter;

  public PostgresqlExecutor() {
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void insert(ServerConfiguration serverConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                    INSERT INTO server_configuration (tenant_id, token_issuer, payload)
                    VALUES (?, ?, ?::jsonb);
                    """;
    String payload = jsonConverter.write(serverConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(serverConfiguration.tenantId());
    params.add(serverConfiguration.tokenIssuer().value());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOne(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
                    SELECT tenant_id, token_issuer, payload
                    FROM server_configuration
                    WHERE tenant_id = ?;
                    """;
    return sqlExecutor.selectOne(sqlTemplate, List.of(tenantIdentifier.value()));
  }
}
