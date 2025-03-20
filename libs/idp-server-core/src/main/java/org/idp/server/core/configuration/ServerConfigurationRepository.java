package org.idp.server.core.configuration;

import org.idp.server.core.tenant.TenantIdentifier;

public interface ServerConfigurationRepository {
  void register(ServerConfiguration serverConfiguration);

  ServerConfiguration get(TenantIdentifier tenantIdentifier);
}
