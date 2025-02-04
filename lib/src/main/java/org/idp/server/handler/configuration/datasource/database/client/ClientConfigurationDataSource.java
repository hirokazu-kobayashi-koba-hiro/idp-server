package org.idp.server.handler.configuration.datasource.database.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.sql.SqlExecutor;
import org.idp.server.basic.sql.TransactionManager;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public class ClientConfigurationDataSource implements ClientConfigurationRepository {

  JsonConverter jsonConverter;

  public ClientConfigurationDataSource() {
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(ClientConfiguration clientConfiguration) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
            INSERT INTO client_configuration (token_issuer, client_id, payload)
            VALUES ('%s', '%s', '%s')
            """;
    String payload = jsonConverter.write(clientConfiguration);
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
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
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

  @Override
  public List<ClientConfiguration> find(TokenIssuer tokenIssuer, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                        SELECT token_issuer, client_id, payload
                        FROM client_configuration
                        WHERE token_issuer = '%s' limit %d offset %d;
                        """;
    String sql = String.format(sqlTemplate, tokenIssuer.value(), limit, offset);
    List<Map<String, String>> maps = sqlExecutor.selectList(sql);
    if (Objects.isNull(maps) || maps.isEmpty()) {
      return List.of();
    }
    return maps.stream().map(ModelConverter::convert).toList();
  }
}
