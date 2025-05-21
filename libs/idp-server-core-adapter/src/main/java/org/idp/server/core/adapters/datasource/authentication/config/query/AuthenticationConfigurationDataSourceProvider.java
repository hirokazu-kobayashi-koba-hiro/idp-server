package org.idp.server.core.adapters.datasource.authentication.config.query;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;

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
