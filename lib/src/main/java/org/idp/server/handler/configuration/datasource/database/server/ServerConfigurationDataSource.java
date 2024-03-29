package org.idp.server.handler.configuration.datasource.database.server;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.type.oauth.TokenIssuer;

public class ServerConfigurationDataSource implements ServerConfigurationRepository {

  SqlConnection sqlConnection;
  JsonConverter jsonConverter;

  public ServerConfigurationDataSource(SqlConnection sqlConnection) {
    this.sqlConnection = sqlConnection;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(ServerConfiguration serverConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
                    INSERT INTO server_configuration (token_issuer, payload)
                    VALUES ('%s', '%s');
                    """;
    String payload = jsonConverter.write(serverConfiguration);
    String sql = String.format(sqlTemplate, serverConfiguration.tokenIssuer().value(), payload);
    sqlExecutor.execute(sql);
  }

  @Override
  public ServerConfiguration get(TokenIssuer tokenIssuer) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
                    SELECT token_issuer, payload
                    FROM server_configuration
                    WHERE token_issuer = '%s';
                    """;
    String sql = String.format(sqlTemplate, tokenIssuer.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tokenIssuer.value()));
    }
    return ModelConverter.convert(stringMap);
  }
}
