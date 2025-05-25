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
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutor;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutorFactory;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutorType;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class FidoUafExecutorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FidoUafExecutorPluginLoader.class);

  public static FidoUafExecutors load(AuthenticationDependencyContainer container) {
    Map<FidoUafExecutorType, FidoUafExecutor> executors = new HashMap<>();

    List<FidoUafExecutorFactory> internals = loadFromInternalModule(FidoUafExecutorFactory.class);
    for (FidoUafExecutorFactory factory : internals) {
      FidoUafExecutor fidoUafExecutor = factory.create(container);
      executors.put(fidoUafExecutor.type(), fidoUafExecutor);
      log.info(
          String.format(
              "Dynamic Registered internal FidoUafExecutor %s", fidoUafExecutor.type().name()));
    }

    List<FidoUafExecutorFactory> externals = loadFromExternalModule(FidoUafExecutorFactory.class);
    for (FidoUafExecutorFactory factory : externals) {
      FidoUafExecutor fidoUafExecutor = factory.create(container);
      executors.put(fidoUafExecutor.type(), fidoUafExecutor);
      log.info(
          String.format(
              "Dynamic Registered external FidoUafExecutor %s", fidoUafExecutor.type().name()));
    }

    return new FidoUafExecutors(executors);
  }
}
