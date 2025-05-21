package org.idp.server.core.adapters.datasource.identity.event;

import org.idp.server.core.oidc.identity.event.UserLifecycleEventResultCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class UserLifecycleEventResultCommandDataSourceProvider
    implements ApplicationComponentProvider<UserLifecycleEventResultCommandRepository> {

  @Override
  public Class<UserLifecycleEventResultCommandRepository> type() {
    return UserLifecycleEventResultCommandRepository.class;
  }

  @Override
  public UserLifecycleEventResultCommandRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new UserLifecycleEventResultCommandDataSource();
  }
}
