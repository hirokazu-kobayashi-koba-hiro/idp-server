package org.idp.server.core.multi_tenancy.tenant;

public interface TenantCommandRepository {

  void register(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
