package org.idp.server.core.security;

import java.util.Map;
import org.idp.server.core.security.hook.SecurityEventHookType;
import org.idp.server.platform.exception.UnSupportedException;

public class SecurityEventHooks {

  Map<SecurityEventHookType, SecurityEventHookExecutor> values;

  public SecurityEventHooks(Map<SecurityEventHookType, SecurityEventHookExecutor> values) {
    this.values = values;
  }

  public SecurityEventHookExecutor get(SecurityEventHookType type) {

    SecurityEventHookExecutor securityEventHookExecutor = values.get(type);

    if (securityEventHookExecutor == null) {
      throw new UnSupportedException("No executor registered for type " + type);
    }

    return securityEventHookExecutor;
  }
}
