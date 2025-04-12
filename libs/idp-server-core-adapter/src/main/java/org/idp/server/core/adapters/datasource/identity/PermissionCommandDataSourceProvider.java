package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;

public class PermissionCommandDataSourceProvider
    implements DataSourceProvider<PermissionCommandRepository> {

  @Override
  public Class<PermissionCommandRepository> type() {
    return PermissionCommandRepository.class;
  }

  @Override
  public PermissionCommandRepository provide(DataSourceDependencyContainer container) {
    return new PermissionCommandDataSource();
  }
}
