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
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.ClientId;

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
            INSERT INTO client_configuration (id, id_alias, tenant_id, payload)
            VALUES (?, ?, ?, ?::jsonb)
            """;

    String payload = jsonConverter.write(clientConfiguration);
    List<Object> params = new ArrayList<>();
    params.add(clientConfiguration.clientId().value());
    params.add(clientConfiguration.clientIdAlias());
    params.add(clientConfiguration.tenantIdentifier().value());
    params.add(payload);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public ClientConfiguration get(Tenant tenant, ClientId clientId) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                    SELECT id, id_alias, tenant_id, payload
                    FROM client_configuration
                    WHERE tenant_id = ? AND id = ?;
                    """;
    List<Object> params = List.of(tenant.identifierValue(), clientId.value());
    Map<String, String> resultClientId = sqlExecutor.selectOne(sqlTemplate, params);

    if (resultClientId != null && !resultClientId.isEmpty()) {
      return ModelConverter.convert(resultClientId);
    }

    String sqlTemplateClientIdAlias =
        """
                        SELECT id, id_alias, tenant_id, payload
                        FROM client_configuration
                        WHERE tenant_id = ? AND id_alias = ?;
                        """;
    List<Object> paramsClientIdAlias = List.of(tenant.identifierValue(), clientId.value());
    Map<String, String> resultClientIdAlias =
        sqlExecutor.selectOne(sqlTemplateClientIdAlias, paramsClientIdAlias);

    if (resultClientIdAlias == null || resultClientIdAlias.isEmpty()) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientId.value()));
    }
    return ModelConverter.convert(resultClientIdAlias);
  }

  @Override
  public List<ClientConfiguration> find(Tenant tenant, int limit, int offset) {
    SqlExecutor sqlExecutor = new SqlExecutor(TransactionManager.getConnection());
    String sqlTemplate =
        """
                        SELECT id, id_alias, tenant_id, payload
                        FROM client_configuration
                        WHERE tenant_id = ? limit ? offset ?;
                        """;
    List<Object> params = List.of(tenant.identifierValue(), limit, offset);
    List<Map<String, String>> maps = sqlExecutor.selectList(sqlTemplate, params);
    if (Objects.isNull(maps) || maps.isEmpty()) {
      return List.of();
    }
    return maps.stream().map(ModelConverter::convert).toList();
  }
}
