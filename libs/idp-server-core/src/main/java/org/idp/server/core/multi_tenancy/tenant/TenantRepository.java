package org.idp.server.core.multi_tenancy.tenant;

public interface TenantRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  Tenant getAdmin();

  void register(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
