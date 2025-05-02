package org.idp.server.core.adapters.datasource.multi_tenancy.tenant;

import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface TenantSqlExecutor {

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);

  Map<String, String> selectAdmin();

  void insert(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
