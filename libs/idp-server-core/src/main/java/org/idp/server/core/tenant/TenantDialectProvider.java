package org.idp.server.core.tenant;

import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.DialectProvider;

public class TenantDialectProvider implements DialectProvider {

  TenantRepository tenantRepository;

  public TenantDialectProvider(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @Override
  public DatabaseType provide(String tenantId) {
    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
    if (AdminTenantContext.isAdmin(tenantIdentifier)) {
      return DatabaseType.POSTGRESQL;
    }

    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return tenant.databaseType();
  }
}
