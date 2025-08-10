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

package org.idp.server.authentication.interactors.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.authentication.interactors.AuthenticationExecutor;
import org.idp.server.authentication.interactors.AuthenticationExecutorFactory;
import org.idp.server.authentication.interactors.AuthenticationExecutors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthenticationExecutorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationExecutorPluginLoader.class);

  public static AuthenticationExecutors load(AuthenticationDependencyContainer container) {
    Map<String, AuthenticationExecutor> executors = new HashMap<>();

    List<AuthenticationExecutorFactory> internals =
        loadFromInternalModule(AuthenticationExecutorFactory.class);
    for (AuthenticationExecutorFactory factory : internals) {
      AuthenticationExecutor executor = factory.create(container);
      executors.put(executor.function(), executor);
      log.info("Dynamic Registered internal AuthenticationExecutor " + executor.function());
    }

    List<AuthenticationExecutorFactory> externals =
        loadFromExternalModule(AuthenticationExecutorFactory.class);
    for (AuthenticationExecutorFactory factory : externals) {
      AuthenticationExecutor executor = factory.create(container);
      executors.put(executor.function(), executor);
      log.info("Dynamic Registered external AuthenticationExecutor " + executor.function());
    }

    return new AuthenticationExecutors(executors);
  }
}
