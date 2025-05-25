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

package org.idp.server.core.oidc.plugin;

import java.util.*;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.identity.event.*;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class UserLifecycleEventExecutorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(UserLifecycleEventExecutorPluginLoader.class);

  public static UserLifecycleEventExecutorsMap load(
      ApplicationComponentContainer applicationComponentContainer,
      AuthenticationDependencyContainer authenticationDependencyContainer) {

    Map<UserLifecycleType, List<UserLifecycleEventExecutor>> internalMap = new HashMap<>();
    List<UserLifecycleEventExecutorFactory> internals =
        loadFromInternalModule(UserLifecycleEventExecutorFactory.class);
    for (UserLifecycleEventExecutorFactory factory : internals) {
      UserLifecycleEventExecutor executor =
          factory.create(applicationComponentContainer, authenticationDependencyContainer);

      UserLifecycleType lifecycleType = executor.lifecycleType();
      internalMap.computeIfAbsent(lifecycleType, k -> new ArrayList<>()).add(executor);

      log.info(
          "Dynamic Registered internal UserLifecycleEventExecutor: "
              + executor.name()
              + " for type "
              + lifecycleType);
    }

    Map<UserLifecycleType, UserLifecycleEventExecutors> resultMap = new HashMap<>();
    for (Map.Entry<UserLifecycleType, List<UserLifecycleEventExecutor>> entry :
        internalMap.entrySet()) {
      resultMap.put(entry.getKey(), new UserLifecycleEventExecutors(entry.getValue()));
    }

    Map<UserLifecycleType, List<UserLifecycleEventExecutor>> externalMap = new HashMap<>();
    List<UserLifecycleEventExecutorFactory> externals =
        loadFromExternalModule(UserLifecycleEventExecutorFactory.class);
    for (UserLifecycleEventExecutorFactory factory : externals) {
      UserLifecycleEventExecutor executor =
          factory.create(applicationComponentContainer, authenticationDependencyContainer);

      UserLifecycleType lifecycleType = executor.lifecycleType();
      externalMap.computeIfAbsent(lifecycleType, k -> new ArrayList<>()).add(executor);

      log.info(
          "Dynamic Registered external UserLifecycleEventExecutor: "
              + executor.name()
              + " for type "
              + lifecycleType);
    }

    for (Map.Entry<UserLifecycleType, List<UserLifecycleEventExecutor>> entry :
        externalMap.entrySet()) {
      resultMap.put(entry.getKey(), new UserLifecycleEventExecutors(entry.getValue()));
    }

    return new UserLifecycleEventExecutorsMap(resultMap);
  }
}
