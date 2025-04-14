package org.idp.server.core.adapters.datasource.configuration.server;

import java.util.Map;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.tenant.TenantIdentifier;

public interface ServerConfigSqlExecutor {
  void insert(ServerConfiguration serverConfiguration);

  Map<String, String> selectOne(TenantIdentifier tenantIdentifier);
}
