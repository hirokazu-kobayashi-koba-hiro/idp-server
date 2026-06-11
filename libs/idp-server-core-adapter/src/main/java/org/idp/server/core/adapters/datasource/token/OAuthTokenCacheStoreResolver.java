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

package org.idp.server.core.adapters.datasource.token;

import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;

public class OAuthTokenCacheStoreResolver {

  private static final String ENV_TOKEN_CACHE_ENABLED = "TOKEN_CACHE_ENABLED";

  public static final int TOKEN_CACHE_TTL_SECONDS = 60;

  public static CacheStore resolve(ApplicationComponentDependencyContainer container) {
    // Default ON (opt-out): introspection reads go through the reader connection, so without the
    // cache a token can be reported inactive right after issuance due to replication lag
    // (Aurora: typically 10-20ms, spikes under write-heavy load; non-Aurora replicas can lag
    // seconds). When the cache backend is disabled (CACHE_ENABLE=false), the resolved CacheStore
    // is a NoOperationCacheStore, so this degrades safely to a no-op.
    String tokenCacheEnabled = System.getenv(ENV_TOKEN_CACHE_ENABLED);
    if ("false".equalsIgnoreCase(tokenCacheEnabled)) {
      return new NoOperationCacheStore();
    }
    return container.resolve(CacheStore.class);
  }
}
