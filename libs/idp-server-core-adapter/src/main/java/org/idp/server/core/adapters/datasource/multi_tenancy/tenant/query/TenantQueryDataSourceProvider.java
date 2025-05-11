package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;

public class TenantQueryDataSourceProvider
    implements ApplicationComponentProvider<TenantQueryRepository> {

  @Override
  public Class<TenantQueryRepository> type() {
    return TenantQueryRepository.class;
  }

  @Override
  public TenantQueryRepository provide(ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);
    return new TenantQueryDataSource(cacheStore);
  }
}
