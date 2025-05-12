package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.adapters.datasource.authentication.config.query.AuthenticationConfigurationQueryDataSource;
import org.idp.server.core.authentication.factory.AuthenticationDependencyProvider;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;

public class AuthenticationConfigurationCommandDataSourceProvider
    implements ApplicationComponentProvider<AuthenticationConfigurationCommandRepository> {

  @Override
  public Class<AuthenticationConfigurationCommandRepository> type() {
    return AuthenticationConfigurationCommandRepository.class;
  }

  @Override
  public AuthenticationConfigurationCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new AuthenticationConfigurationCommandDataSource();
  }
}
