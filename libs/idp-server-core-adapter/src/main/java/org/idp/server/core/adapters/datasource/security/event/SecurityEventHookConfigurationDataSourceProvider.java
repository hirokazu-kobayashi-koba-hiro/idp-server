package org.idp.server.core.adapters.datasource.security.event;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationQueryRepository;

public class SecurityEventHookConfigurationDataSourceProvider
    implements ApplicationComponentProvider<SecurityEventHookConfigurationQueryRepository> {

  @Override
  public Class<SecurityEventHookConfigurationQueryRepository> type() {
    return SecurityEventHookConfigurationQueryRepository.class;
  }

  @Override
  public SecurityEventHookConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new SecurityEventHookConfigurationQueryDataSource();
  }
}
