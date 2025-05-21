package org.idp.server.core.adapters.datasource.identity.permission;

import org.idp.server.core.oidc.identity.permission.PermissionCommandRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

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
