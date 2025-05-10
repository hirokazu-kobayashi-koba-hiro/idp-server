package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.multi_tenancy.tenant.TenantCommandRepository;

public class TenantCommandDataSourceProvider
    implements ApplicationComponentProvider<TenantCommandRepository> {

  @Override
  public Class<TenantCommandRepository> type() {
    return TenantCommandRepository.class;
  }

  @Override
  public TenantCommandRepository provide(ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new TenantCommandDataSource(cacheStore);
  }
}
