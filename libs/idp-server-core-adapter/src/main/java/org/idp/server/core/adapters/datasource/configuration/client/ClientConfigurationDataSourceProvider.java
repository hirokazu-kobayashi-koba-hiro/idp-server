package org.idp.server.core.adapters.datasource.configuration.client;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.configuration.ClientConfigurationRepository;

public class ClientConfigurationDataSourceProvider
    implements ApplicationComponentProvider<ClientConfigurationRepository> {

  @Override
  public Class<ClientConfigurationRepository> type() {
    return ClientConfigurationRepository.class;
  }

  @Override
  public ClientConfigurationRepository provide(ApplicationComponentDependencyContainer container) {
    return new ClientConfigurationDataSource();
  }
}
