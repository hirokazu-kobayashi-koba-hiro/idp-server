package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;

public class ServerConfigurationDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationServerConfigurationRepository> {

  @Override
  public Class<AuthorizationServerConfigurationRepository> type() {
    return AuthorizationServerConfigurationRepository.class;
  }

  @Override
  public AuthorizationServerConfigurationRepository provide(
      ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new AuthorizationServerConfigurationDataSource(cacheStore);
  }
}
