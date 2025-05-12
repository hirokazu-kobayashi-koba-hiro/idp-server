package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigCommandSqlExecutor {
  void insert(Tenant tenant, AuthenticationConfiguration configuration);
}
