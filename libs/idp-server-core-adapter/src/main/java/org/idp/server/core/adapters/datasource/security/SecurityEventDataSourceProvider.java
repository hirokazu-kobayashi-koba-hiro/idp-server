package org.idp.server.core.adapters.datasource.security;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.security.event.SecurityEventRepository;

public class SecurityEventDataSourceProvider
    implements DataSourceDependencyProvider<SecurityEventRepository> {

  @Override
  public Class<SecurityEventRepository> type() {
    return SecurityEventRepository.class;
  }

  @Override
  public SecurityEventRepository provide() {
    return new SecurityEventDataSource();
  }
}
