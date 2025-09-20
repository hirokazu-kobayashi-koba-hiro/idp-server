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

package org.idp.server.core.openid.plugin.authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationInteractor;
import org.idp.server.core.openid.federation.FederationInteractors;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.openid.federation.plugin.FederationInteractorFactory;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class FederationInteractorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(FederationInteractorPluginLoader.class);

  public static FederationInteractors load(FederationDependencyContainer container) {
    Map<FederationType, FederationInteractor> executors = new HashMap<>();

    List<FederationInteractorFactory> internalSsoExecutorServiceLoaders =
        loadFromInternalModule(FederationInteractorFactory.class);
    for (FederationInteractorFactory federationInteractorFactory :
        internalSsoExecutorServiceLoaders) {
      FederationType type = federationInteractorFactory.type();
      FederationInteractor federationInteractor = federationInteractorFactory.create(container);
      executors.put(type, federationInteractor);
      log.info("Dynamic registered internal SSO executor: type={}", type.name());
    }

    List<FederationInteractorFactory> externalSsoExecutorServiceLoaders =
        loadFromExternalModule(FederationInteractorFactory.class);
    for (FederationInteractorFactory federationInteractorFactory :
        externalSsoExecutorServiceLoaders) {
      FederationType type = federationInteractorFactory.type();
      FederationInteractor federationInteractor = federationInteractorFactory.create(container);
      executors.put(type, federationInteractor);
      log.info("Dynamic registered external SSO executor: type={}", type.name());
    }

    return new FederationInteractors(executors);
  }
}
