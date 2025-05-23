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

package org.idp.server.core.oidc.token.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.oidc.token.service.OAuthTokenCreationService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthTokenCreationServiceLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(OAuthTokenCreationServiceLoader.class);

  public static Map<GrantType, OAuthTokenCreationService> load(
      ApplicationComponentContainer container) {
    Map<GrantType, OAuthTokenCreationService> creationServiceMap = new HashMap<>();

    ServiceLoader<OAuthTokenCreationServiceFactory> serviceLoaders =
        ServiceLoader.load(OAuthTokenCreationServiceFactory.class);

    for (OAuthTokenCreationServiceFactory factory : serviceLoaders) {
      OAuthTokenCreationService oAuthTokenCreationService = factory.create(container);
      log.info(
          "Dynamic Registered  OAuthTokenCreationService "
              + oAuthTokenCreationService.getClass().getSimpleName());
      creationServiceMap.put(oAuthTokenCreationService.grantType(), oAuthTokenCreationService);
    }

    return creationServiceMap;
  }
}
