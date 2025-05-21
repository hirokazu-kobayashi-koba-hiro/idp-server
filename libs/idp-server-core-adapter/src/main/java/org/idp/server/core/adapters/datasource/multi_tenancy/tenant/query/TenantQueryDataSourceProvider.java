package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;

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
