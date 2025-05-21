package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;

public class AuthenticationConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<AuthenticationConfigurationCommandRepository> {

  @Override
  public Class<AuthenticationConfigurationCommandRepository> type() {
    return AuthenticationConfigurationCommandRepository.class;
  }

  @Override
  public AuthenticationConfigurationCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new AuthenticationConfigurationCommandDataSource();
  }
}
