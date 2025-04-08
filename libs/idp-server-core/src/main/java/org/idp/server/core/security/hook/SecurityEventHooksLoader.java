package org.idp.server.core.security.hook;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import org.idp.server.core.security.SecurityEventHookExecutor;
import org.idp.server.core.security.SecurityEventHooks;

public class SecurityEventHooksLoader {

  private static final Logger log = Logger.getLogger(SecurityEventHooksLoader.class.getName());

  public static SecurityEventHooks load() {
    Map<SecurityEventHookType, SecurityEventHookExecutor> hookExecutors = new HashMap<>();
    ServiceLoader<SecurityEventHookExecutor> loader =
        ServiceLoader.load(SecurityEventHookExecutor.class);

    for (SecurityEventHookExecutor executor : loader) {
      hookExecutors.put(executor.type(), executor);
      log.info("Dynamic Registered security event hook executor: " + executor.type().name());
    }

    return new SecurityEventHooks(hookExecutors);
  }
}
