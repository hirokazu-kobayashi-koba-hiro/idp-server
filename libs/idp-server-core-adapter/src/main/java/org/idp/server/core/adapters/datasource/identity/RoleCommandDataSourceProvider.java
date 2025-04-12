package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.datasource.DataSourceDependencyProvider;
import org.idp.server.core.oauth.identity.role.RoleCommandRepository;

public class RoleCommandDataSourceProvider
    implements DataSourceDependencyProvider<RoleCommandRepository> {

  @Override
  public Class<RoleCommandRepository> type() {
    return RoleCommandRepository.class;
  }

  @Override
  public RoleCommandRepository provide() {
    return new RoleCommandDataSource();
  }
}
