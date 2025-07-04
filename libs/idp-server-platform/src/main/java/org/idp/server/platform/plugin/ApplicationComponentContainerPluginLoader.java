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

import java.util.List;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class ApplicationComponentContainerPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(ApplicationComponentContainerPluginLoader.class);

  public static ApplicationComponentContainer load(
      ApplicationComponentDependencyContainer dependencyContainer) {
    ApplicationComponentContainer container = new ApplicationComponentContainer();

    List<ApplicationComponentProvider> internals =
        loadFromInternalModule(ApplicationComponentProvider.class);
    for (ApplicationComponentProvider<?> provider : internals) {
      container.register(provider.type(), provider.provide(dependencyContainer));
      log.info("Dynamic Registered internal application component " + provider.type());
    }

    List<ApplicationComponentProvider> externals =
        loadFromExternalModule(ApplicationComponentProvider.class);
    for (ApplicationComponentProvider<?> provider : externals) {
      container.register(provider.type(), provider.provide(dependencyContainer));
      log.info("Dynamic Registered external application component " + provider.type());
    }

    return container;
  }
}
