package org.idp.server.core.adapters.datasource.identity.role;

import org.idp.server.core.identity.role.RoleCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class RoleCommandDataSourceProvider
    implements ApplicationComponentProvider<RoleCommandRepository> {

  @Override
  public Class<RoleCommandRepository> type() {
    return RoleCommandRepository.class;
  }

  @Override
  public RoleCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new RoleCommandDataSource();
  }
}
