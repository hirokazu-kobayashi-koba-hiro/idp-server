package org.idp.server.platform.multi_tenancy.tenant;

public interface TenantMetaDataApi {

  Tenant get(TenantIdentifier tenantIdentifier);
}
