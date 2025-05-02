package org.idp.server.core.multi_tenancy.tenant;

import org.idp.server.basic.datasource.DatabaseType;

public interface DialectProvider {

  DatabaseType provide(TenantIdentifier tenantIdentifier);
}
