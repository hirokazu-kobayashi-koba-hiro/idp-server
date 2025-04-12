package org.idp.server.core.adapters.datasource.oauth.code;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;

public class AuthorizationCodeGrantDataSourceProvider
    implements DataSourceProvider<AuthorizationCodeGrantRepository> {

  @Override
  public Class<AuthorizationCodeGrantRepository> type() {
    return AuthorizationCodeGrantRepository.class;
  }

  @Override
  public AuthorizationCodeGrantRepository provide(DataSourceDependencyContainer container) {
    return new AuthorizationCodeGrantDataSource();
  }
}
