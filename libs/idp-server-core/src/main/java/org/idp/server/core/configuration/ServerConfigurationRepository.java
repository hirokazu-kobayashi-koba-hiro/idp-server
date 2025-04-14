package org.idp.server.core.configuration;

import org.idp.server.core.tenant.Tenant;

public interface ServerConfigurationRepository {
  void register(Tenant tenant, ServerConfiguration serverConfiguration);

  ServerConfiguration get(Tenant tenant);
}
