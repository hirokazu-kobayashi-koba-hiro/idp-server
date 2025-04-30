package org.idp.server.core.identity.permission;

import org.idp.server.core.tenant.Tenant;

public interface PermissionCommandRepository {

  void register(Tenant tenant, Permission permission);

  void bulkRegister(Tenant tenant, Permissions permissions);
}
