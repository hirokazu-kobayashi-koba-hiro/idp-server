package org.idp.server.core.adapters.datasource.configuration.server;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.tenant.Tenant;

public class ServerConfigurationDataSource implements ServerConfigurationRepository {

  ServerConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public ServerConfigurationDataSource() {
    this.executors = new ServerConfigSqlExecutors();
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public void register(Tenant tenant, ServerConfiguration serverConfiguration) {
    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(serverConfiguration);
  }

  @Override
  public ServerConfiguration get(Tenant tenant) {
    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(tenant.identifier());

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tenant.identifierValue()));
    }

    return ModelConverter.convert(stringMap);
  }
}
