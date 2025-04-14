package org.idp.server.core.adapters.datasource.tenant;

import java.util.Map;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;

public interface TenantSqlExecutor {

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);

  Map<String, String> selectAdmin();

  void insert(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
