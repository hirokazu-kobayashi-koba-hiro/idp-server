package org.idp.server.core.adapters.datasource.authentication;

import org.idp.server.core.authentication.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.AuthenticationDependencyProvider;

public class AuthenticationConfigurationDataSourceProvider
    implements AuthenticationDependencyProvider<AuthenticationConfigurationQueryRepository> {

  @Override
  public Class<AuthenticationConfigurationQueryRepository> type() {
    return AuthenticationConfigurationQueryRepository.class;
  }

  @Override
  public AuthenticationConfigurationQueryRepository provide() {
    return new AuthenticationConfigurationQueryDataSource();
  }
}
