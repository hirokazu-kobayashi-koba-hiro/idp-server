package org.idp.server.platform.multi_tenancy.tenant;

import org.idp.server.platform.datasource.DatabaseType;

public interface DialectProvider {

  DatabaseType provide(TenantIdentifier tenantIdentifier);
}
