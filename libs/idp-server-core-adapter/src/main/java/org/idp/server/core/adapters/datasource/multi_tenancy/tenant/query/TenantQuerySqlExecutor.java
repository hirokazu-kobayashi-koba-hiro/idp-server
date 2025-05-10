package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.query;

import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface TenantQuerySqlExecutor {

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);

  Map<String, String> selectAdmin();
}
