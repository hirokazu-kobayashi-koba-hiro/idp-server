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


package org.idp.server.core.oidc.plugin.request;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.factory.AuthorizationRequestObjectFactory;
import org.idp.server.core.oidc.factory.RequestObjectFactoryType;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthorizationRequestFactoryPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestFactoryPluginLoader.class);

  public static Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> load() {
    Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> factories = new HashMap<>();

    ServiceLoader<AuthorizationRequestObjectFactory> serviceLoaders =
        ServiceLoader.load(AuthorizationRequestObjectFactory.class);
    for (AuthorizationRequestObjectFactory requestObjectFactory : serviceLoaders) {
      factories.put(requestObjectFactory.type(), requestObjectFactory);
      log.info(
          "Dynamic Registered AuthorizationRequestObjectFactory "
              + requestObjectFactory.getClass().getSimpleName());
    }

    return factories;
  }
}
