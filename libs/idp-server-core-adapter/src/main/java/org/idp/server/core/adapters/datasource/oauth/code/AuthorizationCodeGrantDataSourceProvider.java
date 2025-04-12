package org.idp.server.core.adapters.datasource.oauth.code;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;

public class AuthorizationCodeGrantDataSourceProvider
    implements DataSourceDependencyProvider<AuthorizationCodeGrantRepository> {

  @Override
  public Class<AuthorizationCodeGrantRepository> type() {
    return AuthorizationCodeGrantRepository.class;
  }

  @Override
  public AuthorizationCodeGrantRepository provide() {
    return new AuthorizationCodeGrantDataSource();
  }
}
