package org.idp.server.core.basic.sql;

import org.idp.server.core.tenant.TenantIdentifier;

public interface DialectProvider {

  Dialect provide(TenantIdentifier tenantIdentifier);
}
