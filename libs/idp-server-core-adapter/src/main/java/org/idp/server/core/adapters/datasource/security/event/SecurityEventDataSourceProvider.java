package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;

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
