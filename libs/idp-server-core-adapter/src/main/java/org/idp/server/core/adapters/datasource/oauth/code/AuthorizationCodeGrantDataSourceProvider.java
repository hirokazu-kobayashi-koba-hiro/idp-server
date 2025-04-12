package org.idp.server.core.adapters.datasource.oauth.code;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;

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
