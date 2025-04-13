package org.idp.server.core.adapters.datasource.identity.permission;

import org.idp.server.core.oauth.identity.permission.Permission;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;
import org.idp.server.core.oauth.identity.permission.Permissions;
import org.idp.server.core.tenant.Tenant;

public class PermissionCommandDataSource implements PermissionCommandRepository {

  PermissionSqlExecutors executors;

  public PermissionCommandDataSource() {
    this.executors = new PermissionSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, Permission permission) {
    PermissionSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(tenant, permission);
  }

  @Override
  public void bulkRegister(Tenant tenant, Permissions permissions) {
    PermissionSqlExecutor executor = executors.get(tenant.dialect());
    executor.bulkInsert(tenant, permissions);
  }
}
