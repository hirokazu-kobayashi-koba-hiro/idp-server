package org.idp.server.core.adapters.datasource.oauth.request;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;

public class AuthorizationRequestDataSourceProvider
    implements DataSourceDependencyProvider<AuthorizationRequestRepository> {

  @Override
  public Class<AuthorizationRequestRepository> type() {
    return AuthorizationRequestRepository.class;
  }

  @Override
  public AuthorizationRequestRepository provide() {
    return new AuthorizationRequestDataSource();
  }
}
