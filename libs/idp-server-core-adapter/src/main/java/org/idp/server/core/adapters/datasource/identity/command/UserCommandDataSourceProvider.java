package org.idp.server.core.adapters.datasource.identity.command;

import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class UserCommandDataSourceProvider
    implements ApplicationComponentProvider<UserCommandRepository> {

  @Override
  public Class<UserCommandRepository> type() {
    return UserCommandRepository.class;
  }

  @Override
  public UserCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new UserCommandDataSource();
  }
}
