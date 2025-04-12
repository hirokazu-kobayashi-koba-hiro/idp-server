package org.idp.server.core.adapters.datasource.configuration.server;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.configuration.ServerConfigurationRepository;

public class ServerConfigurationDataSourceProvider
    implements DataSourceProvider<ServerConfigurationRepository> {

  @Override
  public Class<ServerConfigurationRepository> type() {
    return ServerConfigurationRepository.class;
  }

  @Override
  public ServerConfigurationRepository provide(DataSourceDependencyContainer container) {
    return new ServerConfigurationDataSource();
  }
}
