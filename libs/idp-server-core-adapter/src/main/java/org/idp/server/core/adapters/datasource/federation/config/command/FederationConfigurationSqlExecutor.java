package org.idp.server.core.adapters.datasource.federation.config.command;

import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationSqlExecutor {

  void insert(Tenant tenant, FederationConfiguration configuration);

  void update(Tenant tenant, FederationConfiguration configuration);

  void delete(Tenant tenant, FederationConfiguration configuration);
}
