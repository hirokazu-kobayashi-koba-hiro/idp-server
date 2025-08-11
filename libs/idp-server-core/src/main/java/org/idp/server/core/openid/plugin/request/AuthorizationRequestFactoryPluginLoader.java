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

package org.idp.server.core.openid.plugin.request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.factory.AuthorizationRequestObjectFactory;
import org.idp.server.core.openid.oauth.factory.RequestObjectFactoryType;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

public class AuthorizationRequestFactoryPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestFactoryPluginLoader.class);

  public static Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> load() {
    Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> factories = new HashMap<>();

    List<AuthorizationRequestObjectFactory> internals =
        loadFromInternalModule(AuthorizationRequestObjectFactory.class);
    for (AuthorizationRequestObjectFactory requestObjectFactory : internals) {
      factories.put(requestObjectFactory.type(), requestObjectFactory);
      log.info(
          "Dynamic Registered internal AuthorizationRequestObjectFactory "
              + requestObjectFactory.getClass().getSimpleName());
    }

    List<AuthorizationRequestObjectFactory> externals =
        loadFromExternalModule(AuthorizationRequestObjectFactory.class);
    for (AuthorizationRequestObjectFactory requestObjectFactory : externals) {
      factories.put(requestObjectFactory.type(), requestObjectFactory);
      log.info(
          "Dynamic Registered external AuthorizationRequestObjectFactory "
              + requestObjectFactory.getClass().getSimpleName());
    }

    return factories;
  }
}
