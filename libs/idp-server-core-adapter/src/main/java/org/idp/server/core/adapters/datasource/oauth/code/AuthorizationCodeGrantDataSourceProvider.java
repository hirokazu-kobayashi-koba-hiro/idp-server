package org.idp.server.core.adapters.datasource.oauth.code;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
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
