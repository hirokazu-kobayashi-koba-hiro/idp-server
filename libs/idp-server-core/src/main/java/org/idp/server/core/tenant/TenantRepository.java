package org.idp.server.core.tenant;

public interface TenantRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  Tenant getAdmin();

  void register(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
