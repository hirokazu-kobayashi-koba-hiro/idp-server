package org.idp.server.core.security.repository;

import org.idp.server.core.security.hook.SecurityEventHookConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookConfigurationCommandRepository {

  void register(Tenant tenant, SecurityEventHookConfiguration configuration);

  void update(Tenant tenant, SecurityEventHookConfiguration configuration);

  void delete(Tenant tenant, SecurityEventHookConfiguration configuration);
}
