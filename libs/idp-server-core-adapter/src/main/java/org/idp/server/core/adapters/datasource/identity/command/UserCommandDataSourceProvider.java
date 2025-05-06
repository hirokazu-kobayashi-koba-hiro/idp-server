package org.idp.server.core.adapters.datasource.identity.command;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.repository.UserCommandRepository;

public class UserCommandDataSourceProvider implements ApplicationComponentProvider<UserCommandRepository> {

  @Override
  public Class<UserCommandRepository> type() {
    return UserCommandRepository.class;
  }

  @Override
  public UserCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new UserCommandDataSource();
  }
}
