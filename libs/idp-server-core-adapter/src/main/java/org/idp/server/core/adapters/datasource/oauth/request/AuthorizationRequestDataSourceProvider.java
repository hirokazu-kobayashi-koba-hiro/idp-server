package org.idp.server.core.adapters.datasource.oauth.request;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;

public class AuthorizationRequestDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationRequestRepository> {

  @Override
  public Class<AuthorizationRequestRepository> type() {
    return AuthorizationRequestRepository.class;
  }

  @Override
  public AuthorizationRequestRepository provide(ApplicationComponentDependencyContainer container) {
    return new AuthorizationRequestDataSource();
  }
}
