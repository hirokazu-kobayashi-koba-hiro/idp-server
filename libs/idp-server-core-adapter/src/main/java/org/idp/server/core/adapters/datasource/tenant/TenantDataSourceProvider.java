package org.idp.server.core.adapters.datasource.tenant;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.tenant.TenantRepository;

public class TenantDataSourceProvider implements DataSourceProvider<TenantRepository> {

  @Override
  public Class<TenantRepository> type() {
    return TenantRepository.class;
  }

  @Override
  public TenantRepository provide(DataSourceDependencyContainer container) {
    return new TenantDataSource();
  }
}
