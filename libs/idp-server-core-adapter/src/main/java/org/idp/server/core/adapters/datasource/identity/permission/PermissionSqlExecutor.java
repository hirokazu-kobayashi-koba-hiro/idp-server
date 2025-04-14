package org.idp.server.core.adapters.datasource.identity.permission;

import org.idp.server.core.oauth.identity.permission.Permission;
import org.idp.server.core.oauth.identity.permission.Permissions;
import org.idp.server.core.tenant.Tenant;

public interface PermissionSqlExecutor {

  void insert(Tenant tenant, Permission permission);

  void bulkInsert(Tenant tenant, Permissions permissions);
}
