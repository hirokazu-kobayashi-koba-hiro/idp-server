package org.idp.server.core.adapters.datasource.security;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.security.event.SecurityEventRepository;

public class SecurityEventDataSourceProvider
    implements DataSourceProvider<SecurityEventRepository> {

  @Override
  public Class<SecurityEventRepository> type() {
    return SecurityEventRepository.class;
  }

  @Override
  public SecurityEventRepository provide(DataSourceDependencyContainer container) {
    return new SecurityEventDataSource();
  }
}
