package org.idp.server.core.adapters.datasource.security;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;

public class SecurityEventHookConfigurationDataSourceProvider
    implements DataSourceDependencyProvider<SecurityEventHookConfigurationQueryRepository> {

  @Override
  public Class<SecurityEventHookConfigurationQueryRepository> type() {
    return SecurityEventHookConfigurationQueryRepository.class;
  }

  @Override
  public SecurityEventHookConfigurationQueryRepository provide() {
    return new SecurityEventHookConfigurationQueryDataSource();
  }
}
