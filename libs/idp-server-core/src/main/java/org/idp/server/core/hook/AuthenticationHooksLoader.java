package org.idp.server.core.hook;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public class AuthenticationHooksLoader {

  private static Logger log = Logger.getLogger(AuthenticationHooksLoader.class.getName());

  public static AuthenticationHooks load() {
    Map<HookType, HookExecutor> hookExecutors = new HashMap<>();
    ServiceLoader<HookExecutor> loader = ServiceLoader.load(HookExecutor.class);

    for (HookExecutor executor : loader) {
      hookExecutors.put(executor.type(), executor);
      log.info("Dynamic Registered hook executor: " + executor.type().name());
    }

    return new AuthenticationHooks(hookExecutors);
  }
}
