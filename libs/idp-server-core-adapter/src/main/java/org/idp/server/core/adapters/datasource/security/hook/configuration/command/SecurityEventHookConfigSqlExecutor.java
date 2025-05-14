package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;

public interface SecurityEventHookConfigSqlExecutor {
  void insert(Tenant tenant, SecurityEventHookConfiguration configuration);

  void update(Tenant tenant, SecurityEventHookConfiguration configuration);

  void delete(Tenant tenant, SecurityEventHookConfiguration configuration);
}
