package org.idp.server.core.oauth.identity.role;

import org.idp.server.core.tenant.Tenant;

public interface RoleCommandRepository {

  void register(Tenant tenant, Role role);

  void bulkRegister(Tenant tenant, Roles roles);
}
