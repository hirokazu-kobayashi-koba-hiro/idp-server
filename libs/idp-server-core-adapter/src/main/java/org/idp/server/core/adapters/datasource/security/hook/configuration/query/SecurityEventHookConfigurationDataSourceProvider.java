package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
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
