package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
