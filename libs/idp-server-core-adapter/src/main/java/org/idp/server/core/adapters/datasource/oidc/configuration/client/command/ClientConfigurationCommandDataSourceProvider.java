package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;

public class ClientConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<ClientConfigurationCommandRepository> {

  @Override
  public Class<ClientConfigurationCommandRepository> type() {
    return ClientConfigurationCommandRepository.class;
  }

  @Override
  public ClientConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new ClientConfigurationCommandDataSource(cacheStore);
  }
}
