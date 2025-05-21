package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;

public class ServerConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationServerConfigurationCommandRepository> {

  @Override
  public Class<AuthorizationServerConfigurationCommandRepository> type() {
    return AuthorizationServerConfigurationCommandRepository.class;
  }

  @Override
  public AuthorizationServerConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new AuthorizationServerConfigurationCommandDataSource(cacheStore);
  }
}
