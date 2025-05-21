package org.idp.server.core.adapters.datasource.grant_management;

import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;

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
