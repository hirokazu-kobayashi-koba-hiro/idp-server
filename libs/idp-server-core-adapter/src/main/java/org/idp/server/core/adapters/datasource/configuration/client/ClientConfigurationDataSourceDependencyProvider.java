package org.idp.server.core.adapters.datasource.configuration.client;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.configuration.ClientConfigurationRepository;

public class ClientConfigurationDataSourceDependencyProvider
    implements DataSourceDependencyProvider<ClientConfigurationRepository> {

  @Override
  public Class<ClientConfigurationRepository> type() {
    return ClientConfigurationRepository.class;
  }

  @Override
  public ClientConfigurationRepository provide() {
    return new ClientConfigurationDataSource();
  }
}
