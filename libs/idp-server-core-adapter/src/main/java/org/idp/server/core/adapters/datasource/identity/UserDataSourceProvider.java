package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.oauth.identity.UserRepository;

public class UserDataSourceProvider implements ApplicationComponentProvider<UserRepository> {

  @Override
  public Class<UserRepository> type() {
    return UserRepository.class;
  }

  @Override
  public UserRepository provide(ApplicationComponentDependencyContainer container) {
    return new UserDataSource();
  }
}
