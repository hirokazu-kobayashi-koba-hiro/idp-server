package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.oauth.identity.role.RoleCommandRepository;

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
