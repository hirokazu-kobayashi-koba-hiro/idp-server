package org.idp.server.core.adapters.datasource.configuration.server;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.configuration.ServerConfigurationRepository;

public class ServerConfigurationDataSourceDependencyProvider
    implements DataSourceDependencyProvider<ServerConfigurationRepository> {

  @Override
  public Class<ServerConfigurationRepository> type() {
    return ServerConfigurationRepository.class;
  }

  @Override
  public ServerConfigurationRepository provide() {
    return new ServerConfigurationDataSource();
  }
}
