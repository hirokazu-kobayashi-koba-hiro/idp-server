package org.idp.server.core.security;

import org.idp.server.core.security.hook.*;
import org.idp.server.core.tenant.Tenant;

public interface SecurityEventHookExecutor {

  SecurityEventHookType type();

  SecurityEventHookResult execute(
      Tenant tenant, SecurityEvent securityEvent, SecurityEventHookConfiguration configuration);
}
