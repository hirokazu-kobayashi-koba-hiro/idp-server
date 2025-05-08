package org.idp.server.core.adapters.datasource.multi_tenancy.tenant;

import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

public class TenantDataSourceProvider implements ApplicationComponentProvider<TenantRepository> {

  @Override
  public Class<TenantRepository> type() {
    return TenantRepository.class;
  }

  @Override
  public TenantRepository provide(ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new TenantDataSource(cacheStore);
  }
}
