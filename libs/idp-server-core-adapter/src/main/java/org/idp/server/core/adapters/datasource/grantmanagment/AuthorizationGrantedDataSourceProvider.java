package org.idp.server.core.adapters.datasource.grantmanagment;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;

public class AuthorizationGrantedDataSourceProvider
    implements DataSourceDependencyProvider<AuthorizationGrantedRepository> {

  @Override
  public Class<AuthorizationGrantedRepository> type() {
    return AuthorizationGrantedRepository.class;
  }

  @Override
  public AuthorizationGrantedRepository provide() {
    return new AuthorizationGrantedDataSource();
  }
}
