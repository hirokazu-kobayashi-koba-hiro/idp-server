package org.idp.server.core.adapters.datasource.oidc.configuration.client;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.ClientConfigurationRepository;

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
