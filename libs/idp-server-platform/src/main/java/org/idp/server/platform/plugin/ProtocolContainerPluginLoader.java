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

import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;
import org.idp.server.platform.log.LoggerWrapper;

import java.util.List;

public class ProtocolContainerPluginLoader extends PluginLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ProtocolContainerPluginLoader.class);

  public static ProtocolContainer load(
      ApplicationComponentContainer applicationComponentContainer) {
    ProtocolContainer container = new ProtocolContainer();

    List<ProtocolProvider> internals = loadFromInternalModule(ProtocolProvider.class);
    for (ProtocolProvider<?> provider : internals) {
      container.register(provider.type(), provider.provide(applicationComponentContainer));
      log.info("Dynamic Registered internal Protocol provider " + provider.type());
    }

    List<ProtocolProvider> externals = loadFromExternalModule(ProtocolProvider.class);
    for (ProtocolProvider<?> provider : externals) {
      container.register(provider.type(), provider.provide(applicationComponentContainer));
      log.info("Dynamic Registered external Protocol provider " + provider.type());
    }

    return container;
  }
}
