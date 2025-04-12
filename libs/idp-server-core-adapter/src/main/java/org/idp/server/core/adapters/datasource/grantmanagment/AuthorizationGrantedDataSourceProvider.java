package org.idp.server.core.adapters.datasource.grantmanagment;

import org.idp.server.core.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;

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
