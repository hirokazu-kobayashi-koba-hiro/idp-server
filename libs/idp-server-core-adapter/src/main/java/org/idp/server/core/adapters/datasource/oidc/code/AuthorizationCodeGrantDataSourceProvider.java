package org.idp.server.core.adapters.datasource.oidc.code;

import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuthorizationCodeGrantDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationCodeGrantRepository> {

  @Override
  public Class<AuthorizationCodeGrantRepository> type() {
    return AuthorizationCodeGrantRepository.class;
  }

  @Override
  public AuthorizationCodeGrantRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new AuthorizationCodeGrantDataSource();
  }
}
