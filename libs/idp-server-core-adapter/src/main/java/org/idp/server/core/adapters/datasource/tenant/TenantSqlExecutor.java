package org.idp.server.core.adapters.datasource.tenant;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;

import java.util.Map;

public interface TenantSqlExecutor {

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);

  Map<String, String> selectAdmin();

  void insert(Tenant tenant);

  void update(Tenant tenant);

  void delete(TenantIdentifier tenantIdentifier);
}
