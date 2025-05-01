package org.idp.server.core.adapters.datasource.identity.role;

import org.idp.server.core.identity.role.Role;
import org.idp.server.core.identity.role.RoleCommandRepository;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class RoleCommandDataSource implements RoleCommandRepository {

  RoleSqlExecutors executors;

  public RoleCommandDataSource() {
    this.executors = new RoleSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, Role role) {
    RoleSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, role);
  }

  @Override
  public void bulkRegister(Tenant tenant, Roles roles) {
    RoleSqlExecutor executor = executors.get(tenant.databaseType());
    executor.bulkInsert(tenant, roles);
  }
}
