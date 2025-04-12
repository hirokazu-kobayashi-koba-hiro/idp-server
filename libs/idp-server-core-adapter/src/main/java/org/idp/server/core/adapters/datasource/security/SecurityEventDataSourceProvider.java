package org.idp.server.core.adapters.datasource.security;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.security.event.SecurityEventRepository;

public class SecurityEventDataSourceProvider
    implements ApplicationComponentProvider<SecurityEventRepository> {

  @Override
  public Class<SecurityEventRepository> type() {
    return SecurityEventRepository.class;
  }

  @Override
  public SecurityEventRepository provide(ApplicationComponentDependencyContainer container) {
    return new SecurityEventDataSource();
  }
}
