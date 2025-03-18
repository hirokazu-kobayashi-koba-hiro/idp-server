package org.idp.server.core.adapters.datasource.configuration.database.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

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
            INSERT INTO client_configuration (id, server_id, token_issuer, payload)
            VALUES (?, ?, ?, ?::jsonb)
            """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientId().value());
    params.add(clientConfiguration.serverId());
    params.add(clientConfiguration.tokenIssuer().value());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    SELECT id, server_id, token_issuer, payload
                    FROM client_configuration
                    WHERE token_issuer = ? AND id = ?;
                    """;
    List<Object> params = List.of(tokenIssuer.value(), clientId.value());
    Map<String, String> stringMap = sqlExecutor.selectOne(sqlTemplate, params);

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
                        SELECT id, server_id, token_issuer, payload
                        FROM client_configuration
                        WHERE token_issuer = ? limit ? offset ?;
                        """;
    List<Object> params = List.of(tokenIssuer.value(), limit, offset);
    List<Map<String, String>> maps = sqlExecutor.selectList(sqlTemplate, params);
    if (Objects.isNull(maps) || maps.isEmpty()) {
      return List.of();
    }
    return maps.stream().map(ModelConverter::convert).toList();
  }
}
