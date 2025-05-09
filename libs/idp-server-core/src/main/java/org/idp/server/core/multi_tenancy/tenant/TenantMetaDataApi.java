package org.idp.server.core.multi_tenancy.tenant;

public interface TenantMetaDataApi {

  Tenant get(TenantIdentifier tenantIdentifier);
}
