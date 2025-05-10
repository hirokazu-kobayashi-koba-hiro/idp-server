package org.idp.server.core.multi_tenancy.tenant;

public interface TenantQueryRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  Tenant getAdmin();
}
