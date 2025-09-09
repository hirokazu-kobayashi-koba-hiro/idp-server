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

package org.idp.server.platform.oauth;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class OAuthAuthorizationResolversProvider
    implements ApplicationComponentProvider<OAuthAuthorizationResolvers> {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(OAuthAuthorizationResolversProvider.class);

  @Override
  public Class<OAuthAuthorizationResolvers> type() {
    return OAuthAuthorizationResolvers.class;
  }

  @Override
  public OAuthAuthorizationResolvers provide(ApplicationComponentDependencyContainer container) {
    log.info("Creating OAuthAuthorizationResolvers with cache integration");
    CacheStore cacheStore = container.resolve(CacheStore.class);
    log.info("CacheStore resolved: {}", cacheStore.getClass().getSimpleName());
    // Default buffer and TTL seconds - can be overridden by individual configurations
    OAuthAuthorizationResolvers resolvers = new OAuthAuthorizationResolvers(cacheStore, 60, 3600);
    log.info("OAuthAuthorizationResolvers created successfully with caching enabled");
    return resolvers;
  }
}
