package org.idp.server.core.adapters.datasource.configuration.database.server;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.type.oauth.TokenIssuer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ServerConfigurationDataSource implements ServerConfigurationRepository {

  JsonConverter jsonConverter;

  public ServerConfigurationDataSource() {
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(ServerConfiguration serverConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());

    String sql =
        """
                    INSERT INTO server_configuration (token_issuer, payload)
                    VALUES (?, ?::jsonb);
                    """;
    String payload = jsonConverter.write(serverConfiguration);

    sqlExecutor.execute(sql, List.of(serverConfiguration.tokenIssuer().value(), payload));
  }

  @Override
  public ServerConfiguration get(TokenIssuer tokenIssuer) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    SELECT token_issuer, payload
                    FROM server_configuration
                    WHERE token_issuer = ?;
                    """;
    Map<String, String> stringMap =
        sqlExecutor.selectOne(sqlTemplate, List.of(tokenIssuer.value()));

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tokenIssuer.value()));
    }

    return ModelConverter.convert(stringMap);
  }
}
