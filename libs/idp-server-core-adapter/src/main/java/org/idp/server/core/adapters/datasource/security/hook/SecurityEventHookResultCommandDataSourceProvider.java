package org.idp.server.core.adapters.datasource.security.hook;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;

public class SecurityEventHookResultCommandDataSourceProvider
    implements ApplicationComponentProvider<SecurityEventHookResultCommandRepository> {

  @Override
  public Class<SecurityEventHookResultCommandRepository> type() {
    return SecurityEventHookResultCommandRepository.class;
  }

  @Override
  public SecurityEventHookResultCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new SecurityEventHoolResultCommandDataSource();
  }
}
