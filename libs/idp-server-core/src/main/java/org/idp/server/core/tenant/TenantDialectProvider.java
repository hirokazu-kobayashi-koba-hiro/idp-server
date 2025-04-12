package org.idp.server.core.tenant;

import org.idp.server.core.basic.sql.Dialect;
import org.idp.server.core.basic.sql.DialectProvider;

public class TenantDialectProvider implements DialectProvider {

  TenantRepository tenantRepository;

  public TenantDialectProvider(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @Override
  public Dialect provide(TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return tenant.dialet();
  }
}
