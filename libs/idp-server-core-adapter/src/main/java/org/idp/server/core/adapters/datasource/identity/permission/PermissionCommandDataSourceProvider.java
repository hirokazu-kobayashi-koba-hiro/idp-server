package org.idp.server.core.adapters.datasource.identity.permission;

import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.ApplicationComponentProvider;
import org.idp.server.core.identity.permission.PermissionCommandRepository;

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
