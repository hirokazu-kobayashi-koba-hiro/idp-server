package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;

public class PermissionCommandDataSourceProvider
    implements DataSourceDependencyProvider<PermissionCommandRepository> {

  @Override
  public Class<PermissionCommandRepository> type() {
    return PermissionCommandRepository.class;
  }

  @Override
  public PermissionCommandRepository provide() {
    return new PermissionCommandDataSource();
  }
}
