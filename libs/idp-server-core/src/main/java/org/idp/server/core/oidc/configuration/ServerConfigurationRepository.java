package org.idp.server.core.oidc.configuration;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface ServerConfigurationRepository {
  void register(Tenant tenant, ServerConfiguration serverConfiguration);

  ServerConfiguration get(Tenant tenant);
}
