package org.idp.sample.domain.model.tenant;

public interface TenantRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  void register(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
