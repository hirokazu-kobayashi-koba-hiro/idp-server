package org.idp.server.core.multi_tenancy.tenant;

import org.idp.server.basic.datasource.DatabaseType;

public class TenantDialectProvider implements DialectProvider {

  TenantQueryRepository tenantQueryRepository;

  public TenantDialectProvider(TenantQueryRepository tenantQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public DatabaseType provide(TenantIdentifier tenantIdentifier) {
    if (AdminTenantContext.isAdmin(tenantIdentifier)) {
      return DatabaseType.POSTGRESQL;
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return tenant.databaseType();
  }
}
