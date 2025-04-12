package org.idp.server.core.adapters.datasource.oauth.request;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;

public class AuthorizationRequestDataSourceProvider
    implements DataSourceProvider<AuthorizationRequestRepository> {

  @Override
  public Class<AuthorizationRequestRepository> type() {
    return AuthorizationRequestRepository.class;
  }

  @Override
  public AuthorizationRequestRepository provide(DataSourceDependencyContainer container) {
    return new AuthorizationRequestDataSource();
  }
}
