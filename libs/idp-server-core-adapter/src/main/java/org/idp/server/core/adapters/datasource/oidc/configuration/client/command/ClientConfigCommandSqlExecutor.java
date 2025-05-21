package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientConfigCommandSqlExecutor {

  void insert(Tenant tenant, ClientConfiguration clientConfiguration);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, ClientIdentifier clientIdentifier);
}
