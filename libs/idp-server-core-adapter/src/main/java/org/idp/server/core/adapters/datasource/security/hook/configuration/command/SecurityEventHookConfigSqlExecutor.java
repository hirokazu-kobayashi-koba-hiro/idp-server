package org.idp.server.core.adapters.datasource.security.hook.configuration.command;

import org.idp.server.core.security.hook.SecurityEventHookConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookConfigSqlExecutor {
  void insert(Tenant tenant, SecurityEventHookConfiguration configuration);

  void update(Tenant tenant, SecurityEventHookConfiguration configuration);

  void delete(Tenant tenant, SecurityEventHookConfiguration configuration);
}
