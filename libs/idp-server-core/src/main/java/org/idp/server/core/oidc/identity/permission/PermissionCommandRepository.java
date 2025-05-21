package org.idp.server.core.oidc.identity.permission;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface PermissionCommandRepository {

  void register(Tenant tenant, Permission permission);

  void bulkRegister(Tenant tenant, Permissions permissions);
}
