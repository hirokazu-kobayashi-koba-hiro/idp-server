package org.idp.server.core.identity.role;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface RoleCommandRepository {

  void register(Tenant tenant, Role role);

  void bulkRegister(Tenant tenant, Roles roles);
}
