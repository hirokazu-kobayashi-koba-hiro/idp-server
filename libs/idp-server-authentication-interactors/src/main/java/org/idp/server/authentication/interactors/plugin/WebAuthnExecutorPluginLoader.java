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
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutor;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorFactory;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorType;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutors;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class WebAuthnExecutorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(WebAuthnExecutorPluginLoader.class);

  public static WebAuthnExecutors load(AuthenticationDependencyContainer container) {
    Map<WebAuthnExecutorType, WebAuthnExecutor> executors = new HashMap<>();

    List<WebAuthnExecutorFactory> internals = loadFromInternalModule(WebAuthnExecutorFactory.class);
    for (WebAuthnExecutorFactory factory : internals) {
      WebAuthnExecutor webAuthnExecutor = factory.create(container);
      executors.put(webAuthnExecutor.type(), webAuthnExecutor);
      log.info(
          String.format(
              "Dynamic Registered internal WebAuthnExecutor %s", webAuthnExecutor.type().value()));
    }

    List<WebAuthnExecutorFactory> externals = loadFromExternalModule(WebAuthnExecutorFactory.class);
    for (WebAuthnExecutorFactory factory : externals) {
      WebAuthnExecutor webAuthnExecutor = factory.create(container);
      executors.put(webAuthnExecutor.type(), webAuthnExecutor);
      log.info(
          String.format(
              "Dynamic Registered external WebAuthnExecutor %s", webAuthnExecutor.type().value()));
    }

    return new WebAuthnExecutors(executors);
  }
}
