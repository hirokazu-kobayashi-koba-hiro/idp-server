package org.idp.server.core.adapters.datasource.oidc.configuration.server.query;

import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface ServerConfigSqlExecutor {
  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);
}
