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

package org.idp.server.authentication.interactors.webauthn.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutor;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorFactory;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorType;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class WebAuthnExecutorPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(WebAuthnExecutorPluginLoader.class);

  public static WebAuthnExecutors load(AuthenticationDependencyContainer container) {
    Map<WebAuthnExecutorType, WebAuthnExecutor> executors = new HashMap<>();
    ServiceLoader<WebAuthnExecutorFactory> loader =
        ServiceLoader.load(WebAuthnExecutorFactory.class);

    for (WebAuthnExecutorFactory factory : loader) {
      WebAuthnExecutor webAuthnExecutor = factory.create(container);
      executors.put(webAuthnExecutor.type(), webAuthnExecutor);
      log.info(
          String.format("Dynamic Registered WebAuthnExecutor %s", webAuthnExecutor.type().value()));
    }

    return new WebAuthnExecutors(executors);
  }
}
