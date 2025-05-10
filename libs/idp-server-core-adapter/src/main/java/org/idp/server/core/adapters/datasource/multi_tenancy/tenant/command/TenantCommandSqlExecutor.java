package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.command;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface TenantCommandSqlExecutor {

  void insert(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
