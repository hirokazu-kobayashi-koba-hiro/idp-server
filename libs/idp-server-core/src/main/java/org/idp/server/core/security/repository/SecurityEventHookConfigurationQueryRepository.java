package org.idp.server.core.security.repository;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfigurations;

public interface SecurityEventHookConfigurationQueryRepository {

  SecurityEventHookConfigurations find(Tenant tenant);
}
