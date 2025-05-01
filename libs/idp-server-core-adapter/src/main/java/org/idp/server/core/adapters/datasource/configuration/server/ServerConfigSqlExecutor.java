package org.idp.server.core.adapters.datasource.configuration.server;

import java.util.Map;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface ServerConfigSqlExecutor {
  void insert(ServerConfiguration serverConfiguration);

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);
}
