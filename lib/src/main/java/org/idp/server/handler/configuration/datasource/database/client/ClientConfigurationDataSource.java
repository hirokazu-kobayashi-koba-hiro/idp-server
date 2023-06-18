package org.idp.server.handler.configuration.datasource.database.client;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.basic.sql.SqlConnection;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public class ClientConfigurationDataSource implements ClientConfigurationRepository {

  SqlConnection sqlConnection;
  JsonParser jsonParser;

  public ClientConfigurationDataSource(SqlConnection sqlConnection) {
    this.sqlConnection = sqlConnection;
    this.jsonParser = JsonParser.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
            INSERT INTO client_configuration (token_issuer, client_id, payload)
            VALUES ('%s', '%s', '%s')
            """;
    String payload = jsonParser.write(clientConfiguration);
    String sql =
        String.format(
            sqlTemplate,
            clientConfiguration.tokenIssuer().value(),
            clientConfiguration.clientId().value(),
            payload);
    sqlExecutor.execute(sql);
  }

  @Override
  public ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId) {
    SqlExecutor sqlExecutor = new SqlExecutor(sqlConnection.connection());
    String sqlTemplate =
        """
                    SELECT token_issuer, client_id, payload
                    FROM client_configuration
                    WHERE token_issuer = '%s' AND client_id = '%s';
                    """;
    String sql = String.format(sqlTemplate, tokenIssuer.value(), clientId.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sql);
    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientId.value()));
    }
    return ModelConverter.convert(stringMap);
  }
}
