package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationCommandRepository;

public class SecurityEventHookConfigurationDataSourceProvider
    implements ApplicationComponentProvider<SecurityEventHookConfigurationCommandRepository> {

  @Override
  public Class<SecurityEventHookConfigurationCommandRepository> type() {
    return SecurityEventHookConfigurationCommandRepository.class;
  }

  @Override
  public SecurityEventHookConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new SecurityEventHookConfigurationCommandDataSource();
  }
}
