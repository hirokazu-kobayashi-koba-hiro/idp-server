package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import java.util.Map;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.configuration.ServerConfiguration;

public interface ServerConfigSqlExecutor {
  void insert(ServerConfiguration serverConfiguration);

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);
}
