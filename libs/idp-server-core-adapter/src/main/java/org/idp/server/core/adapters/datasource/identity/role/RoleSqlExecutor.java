package org.idp.server.core.adapters.datasource.identity.role;

import org.idp.server.core.identity.role.Role;
import org.idp.server.core.identity.role.Roles;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface RoleSqlExecutor {

  void insert(Tenant tenant, Role role);

  void bulkInsert(Tenant tenant, Roles roles);
}
