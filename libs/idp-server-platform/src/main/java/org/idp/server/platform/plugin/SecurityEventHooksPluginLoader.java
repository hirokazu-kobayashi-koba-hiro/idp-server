/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.platform.security.SecurityEventHookExecutor;
import org.idp.server.platform.security.SecurityEventHooks;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.hook.SecurityEventHookType;

public class SecurityEventHooksPluginLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventHooksPluginLoader.class);

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
