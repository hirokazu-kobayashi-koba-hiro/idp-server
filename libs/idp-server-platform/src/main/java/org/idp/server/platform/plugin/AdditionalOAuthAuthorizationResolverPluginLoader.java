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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.oauth.OAuthAuthorizationResolver;
import org.idp.server.platform.oauth.OAuthAuthorizationResolverFactory;

public class AdditionalOAuthAuthorizationResolverPluginLoader extends PluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AdditionalOAuthAuthorizationResolverPluginLoader.class);

  public static Map<String, OAuthAuthorizationResolver> load(
      ApplicationComponentContainer container) {

    Map<String, OAuthAuthorizationResolver> resolvers = new HashMap<>();
    List<OAuthAuthorizationResolverFactory> internalOAuthAuthorizationResolverFactories =
        loadFromInternalModule(OAuthAuthorizationResolverFactory.class);
    for (OAuthAuthorizationResolverFactory factory : internalOAuthAuthorizationResolverFactories) {
      OAuthAuthorizationResolver oAuthAuthorizationResolver = factory.create(container);
      resolvers.put(oAuthAuthorizationResolver.type(), oAuthAuthorizationResolver);
      log.info(
          "Dynamic Registered internal OAuthAuthorizationResolver: "
              + oAuthAuthorizationResolver.type());
    }

    List<OAuthAuthorizationResolverFactory> externalOAuthAuthorizationResolverFactories =
        loadFromExternalModule(OAuthAuthorizationResolverFactory.class);
    for (OAuthAuthorizationResolverFactory factory : externalOAuthAuthorizationResolverFactories) {
      OAuthAuthorizationResolver oAuthAuthorizationResolver = factory.create(container);
      resolvers.put(oAuthAuthorizationResolver.type(), oAuthAuthorizationResolver);
      log.info(
          "Dynamic Registered external OAuthAuthorizationResolver: "
              + oAuthAuthorizationResolver.type());
    }

    return resolvers;
  }
}
