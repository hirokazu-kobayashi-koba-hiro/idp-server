package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public interface ClientConfigCommandSqlExecutor {

  void insert(Tenant tenant, ClientConfiguration clientConfiguration);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, RequestedClientId requestedClientId);
}
