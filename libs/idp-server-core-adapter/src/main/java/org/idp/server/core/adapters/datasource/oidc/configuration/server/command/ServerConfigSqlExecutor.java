package org.idp.server.core.adapters.datasource.oidc.configuration.server.command;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public interface ServerConfigSqlExecutor {
  void insert(Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration);

  void update(Tenant tenant, AuthorizationServerConfiguration authorizationServerConfiguration);
}
