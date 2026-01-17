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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.http.SsrfProtectedHttpClient;
import org.idp.server.platform.oauth.cache.SmartCachedOAuthAuthorizationResolver;

public class OAuthAuthorizationResolvers {

  Map<String, OAuthAuthorizationResolver> resolvers = new HashMap<>();
  private final CacheStore cacheStore;
  private final int bufferSeconds;
  private final int defaultTtlSeconds;
  private final SsrfProtectedHttpClient ssrfProtectedHttpClient;

  public OAuthAuthorizationResolvers(
      CacheStore cacheStore,
      int bufferSeconds,
      int defaultTtlSeconds,
      SsrfProtectedHttpClient ssrfProtectedHttpClient) {
    this.cacheStore = cacheStore;
    this.bufferSeconds = bufferSeconds;
    this.defaultTtlSeconds = defaultTtlSeconds;
    this.ssrfProtectedHttpClient = ssrfProtectedHttpClient;
    defaultResolvers();
  }

  private void defaultResolvers() {
    OAuthAuthorizationResolver clientCredentials =
        new ClientCredentialsAuthorizationResolver(ssrfProtectedHttpClient);
    OAuthAuthorizationResolver password =
        new ResourceOwnerPasswordCredentialsAuthorizationResolver(ssrfProtectedHttpClient);

    if (cacheStore != null) {
      clientCredentials =
          new SmartCachedOAuthAuthorizationResolver(
              clientCredentials,
              cacheStore,
              bufferSeconds,
              defaultTtlSeconds,
              ssrfProtectedHttpClient);
      password =
          new SmartCachedOAuthAuthorizationResolver(
              password, cacheStore, bufferSeconds, defaultTtlSeconds, ssrfProtectedHttpClient);
    }

    resolvers.put(clientCredentials.type(), clientCredentials);
    resolvers.put(password.type(), password);
  }

  public OAuthAuthorizationResolver get(String type) {
    OAuthAuthorizationResolver resolver = resolvers.get(type);

    if (resolver == null) {
      throw new UnSupportedException("Unsupported OAuth authorization type: " + type);
    }

    return resolver;
  }
}
