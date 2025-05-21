package org.idp.server.platform.security;

import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SecurityEventHookExecutor {

  SecurityEventHookType type();

  default boolean shouldNotExecute(
      Tenant tenant,
      SecurityEvent securityEvent,
      SecurityEventHookConfiguration hookConfiguration) {
    return !hookConfiguration.hasTrigger(securityEvent.type().value());
  }

  SecurityEventHookResult execute(
      Tenant tenant, SecurityEvent securityEvent, SecurityEventHookConfiguration configuration);
}
