package org.idp.server.core.federation.repository;

import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationCommandRepository {
  void register(Tenant tenant, FederationConfiguration configuration);

  void update(Tenant tenant, FederationConfiguration configuration);

  void delete(Tenant tenant, FederationConfiguration configuration);
}
