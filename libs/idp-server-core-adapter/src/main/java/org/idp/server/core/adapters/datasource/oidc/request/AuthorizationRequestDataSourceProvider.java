package org.idp.server.core.adapters.datasource.oidc.request;

import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
