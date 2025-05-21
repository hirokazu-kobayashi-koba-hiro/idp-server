package org.idp.server.core.multi_tenancy.tenant;

import org.idp.server.platform.datasource.DatabaseType;

public interface DialectProvider {

  DatabaseType provide(TenantIdentifier tenantIdentifier);
}
