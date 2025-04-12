package org.idp.server.core.adapters.datasource.tenant;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.tenant.TenantRepository;

public class TenantDataSourceProvider implements ApplicationComponentProvider<TenantRepository> {

  @Override
  public Class<TenantRepository> type() {
    return TenantRepository.class;
  }

  @Override
  public TenantRepository provide(ApplicationComponentDependencyContainer container) {
    return new TenantDataSource();
  }
}
