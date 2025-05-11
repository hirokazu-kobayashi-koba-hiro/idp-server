package org.idp.server.core.adapters.datasource.oidc.configuration.client.query;

import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;

public class ClientConfigurationQueryDataSourceProvider
    implements ApplicationComponentProvider<ClientConfigurationQueryRepository> {

  @Override
  public Class<ClientConfigurationQueryRepository> type() {
    return ClientConfigurationQueryRepository.class;
  }

  @Override
  public ClientConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new ClientConfigurationQueryDataSource(cacheStore);
  }
}
