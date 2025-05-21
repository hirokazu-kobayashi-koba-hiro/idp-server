package org.idp.server.core.adapters.datasource.oidc.configuration.server.query;

import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
