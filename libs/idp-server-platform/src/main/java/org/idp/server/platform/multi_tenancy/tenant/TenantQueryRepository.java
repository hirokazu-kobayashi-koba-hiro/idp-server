package org.idp.server.platform.multi_tenancy.tenant;

import java.util.List;

public interface TenantQueryRepository {

  Tenant get(TenantIdentifier tenantIdentifier);

  Tenant find(TenantIdentifier tenantIdentifier);

  Tenant getAdmin();

  Tenant findAdmin();

  List<Tenant> findList(List<TenantIdentifier> tenantIdentifiers);
}
