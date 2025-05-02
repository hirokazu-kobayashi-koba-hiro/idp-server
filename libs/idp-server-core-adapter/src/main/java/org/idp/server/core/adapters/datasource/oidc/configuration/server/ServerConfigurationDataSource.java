package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;

public class ServerConfigurationDataSource implements ServerConfigurationRepository {

  ServerConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public ServerConfigurationDataSource() {
    this.executors = new ServerConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
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
