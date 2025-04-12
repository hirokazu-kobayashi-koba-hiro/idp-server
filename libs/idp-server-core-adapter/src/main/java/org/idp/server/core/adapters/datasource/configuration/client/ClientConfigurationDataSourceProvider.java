package org.idp.server.core.adapters.datasource.configuration.client;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.configuration.ClientConfigurationRepository;

public class ClientConfigurationDataSourceProvider
    implements DataSourceProvider<ClientConfigurationRepository> {

  @Override
  public Class<ClientConfigurationRepository> type() {
    return ClientConfigurationRepository.class;
  }

  @Override
  public ClientConfigurationRepository provide(DataSourceDependencyContainer container) {
    return new ClientConfigurationDataSource();
  }
}
