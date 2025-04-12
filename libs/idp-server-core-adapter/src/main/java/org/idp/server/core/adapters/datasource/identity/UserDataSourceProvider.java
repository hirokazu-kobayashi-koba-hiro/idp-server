package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.oauth.identity.UserRepository;

public class UserDataSourceProvider implements DataSourceDependencyProvider<UserRepository> {

  @Override
  public Class<UserRepository> type() {
    return UserRepository.class;
  }

  @Override
  public UserRepository provide() {
    return new UserDataSource();
  }
}
