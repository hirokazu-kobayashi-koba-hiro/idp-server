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

package org.idp.server.core.oidc.plugin.authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.AuthenticationInteractors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthenticationInteractorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationInteractorPluginLoader.class);

  public static AuthenticationInteractors load(AuthenticationDependencyContainer container) {
    Map<AuthenticationInteractionType, AuthenticationInteractor> interactors = new HashMap<>();

    List<AuthenticationInteractorFactory> internals =
        loadFromInternalModule(AuthenticationInteractorFactory.class);
    for (AuthenticationInteractorFactory factory : internals) {
      AuthenticationInteractor interactor = factory.create(container);
      interactors.put(factory.type(), interactor);
      log.info("Dynamic Registered internal Authentication interactor: " + factory.type().name());
    }

    List<AuthenticationInteractorFactory> externals =
        loadFromExternalModule(AuthenticationInteractorFactory.class);
    for (AuthenticationInteractorFactory factory : externals) {
      AuthenticationInteractor interactor = factory.create(container);
      interactors.put(factory.type(), interactor);
      log.info("Dynamic Registered external Authentication interactor: " + factory.type().name());
    }

    return new AuthenticationInteractors(interactors);
  }
}
