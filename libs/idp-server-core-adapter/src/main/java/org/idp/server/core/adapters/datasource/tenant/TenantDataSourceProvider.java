package org.idp.server.core.adapters.datasource.tenant;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.tenant.TenantRepository;

public class TenantDataSourceProvider implements DataSourceDependencyProvider<TenantRepository> {

  @Override
  public Class<TenantRepository> type() {
    return TenantRepository.class;
  }

  @Override
  public TenantRepository provide() {
    return new TenantDataSource();
  }
}
