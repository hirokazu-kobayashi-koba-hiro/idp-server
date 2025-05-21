package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigCommandSqlExecutor {
  void insert(Tenant tenant, AuthenticationConfiguration configuration);

  void update(Tenant tenant, AuthenticationConfiguration configuration);

  void delete(Tenant tenant, AuthenticationConfiguration configuration);
}
