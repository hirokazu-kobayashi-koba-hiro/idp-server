package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.exception.ServerConfigurationNotFoundException;

public class AuthorizationServerConfigurationDataSource
    implements AuthorizationServerConfigurationRepository {

  ServerConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthorizationServerConfigurationDataSource() {
    this.executors = new ServerConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(
      Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration) {
    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(authorizationServerConfiguration);
  }

  @Override
  public AuthorizationServerConfiguration get(Tenant tenant) {
    ServerConfigSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(tenant.identifier());

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tenant.identifierValue()));
    }

    return ModelConverter.convert(stringMap);
  }
}
