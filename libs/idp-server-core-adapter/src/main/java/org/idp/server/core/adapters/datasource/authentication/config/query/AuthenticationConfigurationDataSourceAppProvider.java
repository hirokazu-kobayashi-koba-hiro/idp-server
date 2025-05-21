package org.idp.server.core.adapters.datasource.authentication.config.query;

import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuthenticationConfigurationDataSourceAppProvider
    implements ApplicationComponentProvider<AuthenticationConfigurationQueryRepository> {

  @Override
  public Class<AuthenticationConfigurationQueryRepository> type() {
    return AuthenticationConfigurationQueryRepository.class;
  }

  @Override
  public AuthenticationConfigurationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new AuthenticationConfigurationQueryDataSource();
  }
}
