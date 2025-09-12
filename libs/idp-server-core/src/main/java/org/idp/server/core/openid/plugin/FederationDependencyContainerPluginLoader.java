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

package org.idp.server.core.openid.plugin;

import java.util.List;
import org.idp.server.core.openid.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.openid.federation.plugin.FederationDependencyProvider;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class FederationDependencyContainerPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationDependencyContainerPluginLoader.class);

  public static FederationDependencyContainer load(
      ApplicationComponentDependencyContainer appContainer) {
    FederationDependencyContainer container = new FederationDependencyContainer();

    List<FederationDependencyProvider> internals =
        loadFromInternalModule(FederationDependencyProvider.class);
    for (FederationDependencyProvider<?> provider : internals) {
      container.register(provider.type(), provider.provide(appContainer));
      log.info("Dynamic Registered internal federation dependency provider " + provider.type());
    }

    List<FederationDependencyProvider> externals =
        loadFromExternalModule(FederationDependencyProvider.class);
    for (FederationDependencyProvider<?> provider : externals) {
      container.register(provider.type(), provider.provide(appContainer));
      log.info("Dynamic Registered external federation dependency provider " + provider.type());
    }

    return container;
  }
}
