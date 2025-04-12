package org.idp.server.core.adapters.datasource.grantmanagment;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;

public class AuthorizationGrantedDataSourceProvider
    implements DataSourceProvider<AuthorizationGrantedRepository> {

  @Override
  public Class<AuthorizationGrantedRepository> type() {
    return AuthorizationGrantedRepository.class;
  }

  @Override
  public AuthorizationGrantedRepository provide(DataSourceDependencyContainer container) {
    return new AuthorizationGrantedDataSource();
  }
}
