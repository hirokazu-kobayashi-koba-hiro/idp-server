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

package org.idp.server.federation.sso.oidc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.DependencyAwarePluginLoader;

public class OidcSsoExecutorPluginLoader
    extends DependencyAwarePluginLoader<OidcSsoExecutor, OidcSsoExecutorFactory> {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(OidcSsoExecutorPluginLoader.class);

  public static OidcSsoExecutors load(ApplicationComponentDependencyContainer container) {
    Map<SsoProvider, OidcSsoExecutor> executors = new HashMap<>();

    List<OidcSsoExecutorFactory> internalFactories =
        loadFromInternalModule(OidcSsoExecutorFactory.class);
    for (OidcSsoExecutorFactory factory : internalFactories) {
      OidcSsoExecutor executor = factory.create(container);
      SsoProvider provider = factory.ssoProvider();
      executors.put(provider, executor);
      log.info("Dynamic registered internal SSO provider via factory: name={}", provider.name());
    }

    List<OidcSsoExecutorFactory> externalFactories =
        loadFromExternalModule(OidcSsoExecutorFactory.class);
    for (OidcSsoExecutorFactory factory : externalFactories) {
      OidcSsoExecutor executor = factory.create(container);
      SsoProvider provider = factory.ssoProvider();
      executors.put(provider, executor);
      log.info("Dynamic registered external SSO provider via factory: name={}", provider.name());
    }

    return new OidcSsoExecutors(executors);
  }

  public static OidcSsoExecutors load() {
    Map<SsoProvider, OidcSsoExecutor> executors = new HashMap<>();

    List<OidcSsoExecutor> internals = loadFromInternalModule(OidcSsoExecutor.class);
    for (OidcSsoExecutor executor : internals) {
      SsoProvider provider = executor.type();
      executors.put(provider, executor);
      log.info("Dynamic registered internal SSO provider: name={}", provider.name());
    }

    List<OidcSsoExecutor> externals = loadFromExternalModule(OidcSsoExecutor.class);
    for (OidcSsoExecutor executor : externals) {
      SsoProvider provider = executor.type();
      executors.put(provider, executor);
      log.info("Dynamic registered external SSO provider: name={}", provider.name());
    }

    return new OidcSsoExecutors(executors);
  }
}
