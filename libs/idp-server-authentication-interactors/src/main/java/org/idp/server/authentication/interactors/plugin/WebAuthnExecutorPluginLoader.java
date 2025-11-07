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
import org.idp.server.authentication.interactors.fido2.Fido2Executor;
import org.idp.server.authentication.interactors.fido2.Fido2ExecutorFactory;
import org.idp.server.authentication.interactors.fido2.Fido2ExecutorType;
import org.idp.server.authentication.interactors.fido2.Fido2Executors;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class WebAuthnExecutorPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(WebAuthnExecutorPluginLoader.class);

  public static Fido2Executors load(AuthenticationDependencyContainer container) {
    Map<Fido2ExecutorType, Fido2Executor> executors = new HashMap<>();

    List<Fido2ExecutorFactory> internals = loadFromInternalModule(Fido2ExecutorFactory.class);
    for (Fido2ExecutorFactory factory : internals) {
      Fido2Executor fido2Executor = factory.create(container);
      executors.put(fido2Executor.type(), fido2Executor);
      log.info(
          String.format(
              "Dynamic Registered internal WebAuthnExecutor %s", fido2Executor.type().value()));
    }

    List<Fido2ExecutorFactory> externals = loadFromExternalModule(Fido2ExecutorFactory.class);
    for (Fido2ExecutorFactory factory : externals) {
      Fido2Executor fido2Executor = factory.create(container);
      executors.put(fido2Executor.type(), fido2Executor);
      log.info(
          String.format(
              "Dynamic Registered external WebAuthnExecutor %s", fido2Executor.type().value()));
    }

    return new Fido2Executors(executors);
  }
}
