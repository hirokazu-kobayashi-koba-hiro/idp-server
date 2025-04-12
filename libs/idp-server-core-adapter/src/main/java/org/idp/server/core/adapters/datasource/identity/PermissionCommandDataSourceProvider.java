package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.dependencies.ApplicationComponentDependencyContainer;
import org.idp.server.core.basic.dependencies.ApplicationComponentProvider;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;

public class PermissionCommandDataSourceProvider
    implements ApplicationComponentProvider<PermissionCommandRepository> {

  @Override
  public Class<PermissionCommandRepository> type() {
    return PermissionCommandRepository.class;
  }

  @Override
  public PermissionCommandRepository provide(ApplicationComponentDependencyContainer container) {
    return new PermissionCommandDataSource();
  }
}
