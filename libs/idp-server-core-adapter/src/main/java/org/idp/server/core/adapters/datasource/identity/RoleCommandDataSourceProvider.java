package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceProvider;
import org.idp.server.core.oauth.identity.role.RoleCommandRepository;

public class RoleCommandDataSourceProvider implements DataSourceProvider<RoleCommandRepository> {

  @Override
  public Class<RoleCommandRepository> type() {
    return RoleCommandRepository.class;
  }

  @Override
  public RoleCommandRepository provide(DataSourceDependencyContainer container) {
    return new RoleCommandDataSource();
  }
}
