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

package org.idp.server.authenticators.webauthn4j.mds;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.junit.jupiter.api.Test;

class MdsResolverFactoryTest {

  private final CacheStore cacheStore = new InMemoryCacheStore();

  @Test
  void create_shouldReturnNoOperation_whenConfigIsNull() {
    MdsResolver resolver = MdsResolverFactory.create(null, cacheStore);

    assertInstanceOf(NoOperationMdsResolver.class, resolver);
  }

  @Test
  void create_shouldReturnNoOperation_whenDisabled() {
    MdsConfiguration config = new MdsConfiguration(false);

    MdsResolver resolver = MdsResolverFactory.create(config, cacheStore);

    assertInstanceOf(NoOperationMdsResolver.class, resolver);
  }

  @Test
  void create_shouldReturnCachedResolver_whenEnabled() {
    MdsConfiguration config = new MdsConfiguration(true);

    MdsResolver resolver = MdsResolverFactory.create(config, cacheStore);

    assertInstanceOf(CachedMdsResolver.class, resolver);
  }

  @Test
  void create_shouldReturnCachedResolver_withCustomTtl() {
    MdsConfiguration config = new MdsConfiguration(true, 3600);

    MdsResolver resolver = MdsResolverFactory.create(config, cacheStore);

    assertInstanceOf(CachedMdsResolver.class, resolver);
  }

  /** Simple in-memory cache store for testing */
  private static class InMemoryCacheStore implements CacheStore {
    private final Map<String, Object> cache = new HashMap<>();

    @Override
    public <T> void put(String key, T value) {
      cache.put(key, value);
    }

    @Override
    public <T> void put(String key, T value, int timeToLiveSeconds) {
      cache.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> find(String key, Class<T> type) {
      Object value = cache.get(key);
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of((T) value);
    }

    @Override
    public boolean exists(String key) {
      return cache.containsKey(key);
    }

    @Override
    public void delete(String key) {
      cache.remove(key);
    }

    @Override
    public long increment(String key, int timeToLiveSeconds) {
      return 0;
    }
  }
}
