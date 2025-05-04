package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.security.repository.SecurityEventCommandRepository;

public class SecurityEventDataSourceProvider
    implements ApplicationComponentProvider<SecurityEventCommandRepository> {

  @Override
  public Class<SecurityEventCommandRepository> type() {
    return SecurityEventCommandRepository.class;
  }

  @Override
  public SecurityEventCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new SecurityEventCommandDataSource();
  }
}
