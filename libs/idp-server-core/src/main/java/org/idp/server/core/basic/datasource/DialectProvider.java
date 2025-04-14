package org.idp.server.core.basic.datasource;

import org.idp.server.core.tenant.TenantIdentifier;

public interface DialectProvider {

  DatabaseType provide(TenantIdentifier tenantIdentifier);
}
