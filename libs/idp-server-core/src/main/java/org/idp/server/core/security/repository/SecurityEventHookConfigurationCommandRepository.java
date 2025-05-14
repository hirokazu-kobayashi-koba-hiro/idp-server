package org.idp.server.core.security.repository;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationIdentifier;
import org.idp.server.core.security.hook.SecurityEventHookConfigurations;

public interface SecurityEventHookConfigurationCommandRepository {

  void register(Tenant tenant, SecurityEventHookConfiguration configuration);

  void update(Tenant tenant, SecurityEventHookConfiguration configuration);

  void delete(Tenant tenant, SecurityEventHookConfiguration configuration);
}
