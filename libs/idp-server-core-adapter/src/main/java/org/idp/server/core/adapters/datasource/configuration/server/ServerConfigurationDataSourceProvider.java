package org.idp.server.core.adapters.datasource.configuration.server;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.configuration.ServerConfigurationRepository;

public class ServerConfigurationDataSourceProvider
    implements ApplicationComponentProvider<ServerConfigurationRepository> {

  @Override
  public Class<ServerConfigurationRepository> type() {
    return ServerConfigurationRepository.class;
  }

  @Override
  public ServerConfigurationRepository provide(ApplicationComponentDependencyContainer container) {
    return new ServerConfigurationDataSource();
  }
}
