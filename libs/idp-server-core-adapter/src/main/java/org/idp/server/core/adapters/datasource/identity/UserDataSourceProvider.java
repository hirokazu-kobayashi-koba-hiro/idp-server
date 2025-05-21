package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class UserDataSourceProvider implements ApplicationComponentProvider<UserQueryRepository> {

  @Override
  public Class<UserQueryRepository> type() {
    return UserQueryRepository.class;
  }

  @Override
  public UserQueryRepository provide(ApplicationComponentDependencyContainer container) {
    return new UserQueryDataSource();
  }
}
