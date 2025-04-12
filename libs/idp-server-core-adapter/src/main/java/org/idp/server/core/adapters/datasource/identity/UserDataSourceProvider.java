package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.oauth.identity.UserRepository;

public class UserDataSourceProvider implements DataSourceProvider<UserRepository> {

  @Override
  public Class<UserRepository> type() {
    return UserRepository.class;
  }

  @Override
  public UserRepository provide(DataSourceDependencyContainer container) {
    return new UserDataSource();
  }
}
