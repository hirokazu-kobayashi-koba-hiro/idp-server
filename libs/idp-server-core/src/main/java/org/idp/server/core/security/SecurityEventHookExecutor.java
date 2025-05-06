package org.idp.server.core.security;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.security.hook.*;

public interface SecurityEventHookExecutor {

  SecurityEventHookType type();

  default boolean shouldNotExecute(Tenant tenant, SecurityEvent securityEvent, SecurityEventHookConfiguration hookConfiguration) {
    return !hookConfiguration.hasTrigger(securityEvent.type().value());
  }

  SecurityEventHookResult execute(Tenant tenant, SecurityEvent securityEvent, SecurityEventHookConfiguration configuration);
}
