package org.idp.server.core.adapters.datasource.configuration.database.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.tenant.TenantIdentifier;

public class ServerConfigurationDataSource implements ServerConfigurationRepository {

  JsonConverter jsonConverter;

  public ServerConfigurationDataSource() {
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(ServerConfiguration serverConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

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
  public ServerConfiguration get(TenantIdentifier tenantIdentifier) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    SELECT tenant_id, token_issuer, payload
                    FROM server_configuration
                    WHERE tenant_id = ?;
                    """;
    Map<String, String> stringMap =
        sqlExecutor.selectOne(sqlTemplate, List.of(tenantIdentifier.value()));

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tenantIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }
}
