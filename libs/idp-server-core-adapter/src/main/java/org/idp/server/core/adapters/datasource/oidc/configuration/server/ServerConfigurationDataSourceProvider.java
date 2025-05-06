package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;

public class ServerConfigurationDataSourceProvider implements ApplicationComponentProvider<ServerConfigurationRepository> {

  @Override
  public Class<ServerConfigurationRepository> type() {
    return ServerConfigurationRepository.class;
  }

  @Override
  public ServerConfigurationRepository provide(ApplicationComponentDependencyContainer container) {
    return new ServerConfigurationDataSource();
  }
}
