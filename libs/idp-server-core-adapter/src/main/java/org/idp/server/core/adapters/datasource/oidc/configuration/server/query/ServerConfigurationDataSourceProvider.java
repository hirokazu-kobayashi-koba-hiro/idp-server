package org.idp.server.core.adapters.datasource.oidc.configuration.server.query;

import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;

public class ServerConfigurationDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationServerConfigurationQueryRepository> {

  @Override
  public Class<AuthorizationServerConfigurationQueryRepository> type() {
    return AuthorizationServerConfigurationQueryRepository.class;
  }

  @Override
  public AuthorizationServerConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new AuthorizationServerConfigurationQueryDataSource(cacheStore);
  }
}
