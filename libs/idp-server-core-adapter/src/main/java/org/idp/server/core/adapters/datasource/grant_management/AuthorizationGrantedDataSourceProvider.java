package org.idp.server.core.adapters.datasource.grant_management;

import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuthorizationGrantedDataSourceProvider
    implements ApplicationComponentProvider<AuthorizationGrantedRepository> {

  @Override
  public Class<AuthorizationGrantedRepository> type() {
    return AuthorizationGrantedRepository.class;
  }

  @Override
  public AuthorizationGrantedRepository provide(ApplicationComponentDependencyContainer container) {
    return new AuthorizationGrantedDataSource();
  }
}
