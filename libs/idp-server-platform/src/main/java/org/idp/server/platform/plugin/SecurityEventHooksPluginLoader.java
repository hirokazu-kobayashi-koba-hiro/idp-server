/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.hook.SecurityEventHook;
import org.idp.server.platform.security.hook.SecurityEventHookFactory;
import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.hook.SecurityEventHooks;

public class SecurityEventHooksPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SecurityEventHooksPluginLoader.class);

  public static SecurityEventHooks load(ApplicationComponentContainer container) {
    Map<SecurityEventHookType, SecurityEventHook> hookExecutors = new HashMap<>();

    List<SecurityEventHookFactory> internalHookFactories =
        loadFromInternalModule(SecurityEventHookFactory.class);
    for (SecurityEventHookFactory factory : internalHookFactories) {

      SecurityEventHook executor = factory.create(container);
      hookExecutors.put(executor.type(), executor);
      log.info(
          "Dynamic Registered internal security event hook executor: " + executor.type().name());
    }

    List<SecurityEventHookFactory> externalHookFactories =
        loadFromExternalModule(SecurityEventHookFactory.class);
    for (SecurityEventHookFactory factory : externalHookFactories) {
      SecurityEventHook executor = factory.create(container);
      hookExecutors.put(executor.type(), executor);
      log.info(
          "Dynamic Registered external security event hook executor: " + executor.type().name());
    }

    return new SecurityEventHooks(hookExecutors);
  }
}
