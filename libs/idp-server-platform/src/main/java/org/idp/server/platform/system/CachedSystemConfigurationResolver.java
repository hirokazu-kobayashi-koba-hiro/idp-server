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

package org.idp.server.platform.system;

import java.util.Optional;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Cached implementation of SystemConfigurationResolver.
 *
 * <p>This resolver caches the system configuration to avoid repeated database access. The cache is
 * automatically managed with a configurable TTL.
 *
 * <p>Cache invalidation occurs:
 *
 * <ul>
 *   <li>When {@link #invalidateCache()} is explicitly called
 *   <li>When the cache TTL expires
 * </ul>
 */
public class CachedSystemConfigurationResolver implements SystemConfigurationResolver {

  private static final String CACHE_KEY = "system_configuration";
  private static final int DEFAULT_TTL_SECONDS = 300; // 5 minutes

  private final SystemConfigurationRepository repository;
  private final CacheStore cacheStore;
  private final int ttlSeconds;
  private final LoggerWrapper log =
      LoggerWrapper.getLogger(CachedSystemConfigurationResolver.class);

  public CachedSystemConfigurationResolver(
      SystemConfigurationRepository repository, CacheStore cacheStore) {
    this(repository, cacheStore, DEFAULT_TTL_SECONDS);
  }

  public CachedSystemConfigurationResolver(
      SystemConfigurationRepository repository, CacheStore cacheStore, int ttlSeconds) {
    this.repository = repository;
    this.cacheStore = cacheStore;
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public SystemConfiguration resolve() {
    Optional<SystemConfiguration> cached = cacheStore.find(CACHE_KEY, SystemConfiguration.class);

    if (cached.isPresent()) {
      log.debug("Using cached system configuration");
      return cached.get();
    }

    log.debug("Cache miss for system configuration, fetching from repository");
    SystemConfiguration configuration = repository.find();

    cacheStore.put(CACHE_KEY, configuration, ttlSeconds);
    log.debug("Cached system configuration with TTL: {}s", ttlSeconds);

    return configuration;
  }

  @Override
  public void invalidateCache() {
    cacheStore.delete(CACHE_KEY);
    log.info("Invalidated system configuration cache");
  }
}
