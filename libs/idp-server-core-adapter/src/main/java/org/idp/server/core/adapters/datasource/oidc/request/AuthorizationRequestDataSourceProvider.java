package org.idp.server.core.adapters.datasource.oidc.request;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;

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
