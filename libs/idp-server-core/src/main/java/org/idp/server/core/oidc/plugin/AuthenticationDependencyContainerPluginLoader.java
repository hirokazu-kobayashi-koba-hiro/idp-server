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

import java.util.List;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthenticationDependencyContainerPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationDependencyContainerPluginLoader.class);

  public static AuthenticationDependencyContainer load() {
    AuthenticationDependencyContainer container = new AuthenticationDependencyContainer();

    List<AuthenticationDependencyProvider> internals =
        loadFromInternalModule(AuthenticationDependencyProvider.class);
    for (AuthenticationDependencyProvider<?> provider : internals) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered internal Authentication dependency provider " + provider.type());
    }

    List<AuthenticationDependencyProvider> externals =
        loadFromExternalModule(AuthenticationDependencyProvider.class);
    for (AuthenticationDependencyProvider<?> provider : externals) {
      container.register(provider.type(), provider.provide());
      log.info("Dynamic Registered external Authentication dependency provider " + provider.type());
    }

    return container;
  }
}
