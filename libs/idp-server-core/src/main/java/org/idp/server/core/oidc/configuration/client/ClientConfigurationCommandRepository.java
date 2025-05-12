package org.idp.server.core.oidc.configuration.client;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface ClientConfigurationCommandRepository {

  void register(Tenant tenant, ClientConfiguration clientConfiguration);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, ClientConfiguration clientConfiguration);
}
