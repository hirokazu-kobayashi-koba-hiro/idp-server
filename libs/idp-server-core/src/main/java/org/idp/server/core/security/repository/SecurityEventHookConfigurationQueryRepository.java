package org.idp.server.core.security.repository;

import java.util.List;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationIdentifier;
import org.idp.server.core.security.hook.SecurityEventHookConfigurations;

public interface SecurityEventHookConfigurationQueryRepository {

  SecurityEventHookConfigurations find(Tenant tenant);

  SecurityEventHookConfiguration find(
      Tenant tenant, SecurityEventHookConfigurationIdentifier identifier);

  List<SecurityEventHookConfiguration> findList(Tenant tenant, int limit, int offset);
}
