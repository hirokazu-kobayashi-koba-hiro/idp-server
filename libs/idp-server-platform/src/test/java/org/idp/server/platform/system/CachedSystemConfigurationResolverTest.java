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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.system.config.SsrfProtectionConfig;
import org.idp.server.platform.system.config.TrustedProxyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachedSystemConfigurationResolverTest {

  private InMemoryCacheStore cacheStore;
  private CountingRepository repository;
  private CachedSystemConfigurationResolver resolver;

  @BeforeEach
  void setUp() {
    cacheStore = new InMemoryCacheStore();
    repository = new CountingRepository();
    resolver = new CachedSystemConfigurationResolver(repository, cacheStore);
  }

  @Test
  void resolve_shouldFetchFromRepository_whenCacheIsEmpty() {
    SystemConfiguration config = resolver.resolve();

    assertNotNull(config);
    assertEquals(1, repository.getCallCount());
  }

  @Test
  void resolve_shouldUseCache_onSecondCall() {
    // First call - should fetch from repository
    resolver.resolve();
    assertEquals(1, repository.getCallCount());

    // Second call - should use cache
    resolver.resolve();
    assertEquals(1, repository.getCallCount());
  }

  @Test
  void resolve_shouldReturnCorrectConfig() {
    SsrfProtectionConfig ssrfConfig =
        new SsrfProtectionConfig(true, Set.of("localhost"), Set.of("api.example.com"));
    TrustedProxyConfig proxyConfig = TrustedProxyConfig.defaultConfig();
    repository.setConfiguration(new SystemConfiguration(ssrfConfig, proxyConfig));

    SystemConfiguration config = resolver.resolve();

    assertTrue(config.ssrf().isEnabled());
    assertTrue(config.ssrf().isBypassHost("localhost"));
    assertTrue(config.ssrf().isAllowedHost("api.example.com"));
  }

  @Test
  void invalidateCache_shouldCauseRefetch() {
    // First call
    resolver.resolve();
    assertEquals(1, repository.getCallCount());

    // Invalidate cache
    resolver.invalidateCache();

    // Next call should fetch again
    resolver.resolve();
    assertEquals(2, repository.getCallCount());
  }

  @Test
  void invalidateCache_shouldAllowNewConfig() {
    // First configuration
    SsrfProtectionConfig config1 = new SsrfProtectionConfig(true, Set.of("localhost"), null);
    TrustedProxyConfig proxyConfig = TrustedProxyConfig.defaultConfig();
    repository.setConfiguration(new SystemConfiguration(config1, proxyConfig));

    SystemConfiguration resolved1 = resolver.resolve();
    assertTrue(resolved1.ssrf().isBypassHost("localhost"));

    // Update configuration in repository
    SsrfProtectionConfig config2 = new SsrfProtectionConfig(true, Set.of("new-host"), null);
    repository.setConfiguration(new SystemConfiguration(config2, proxyConfig));

    // Without invalidation, still returns cached value
    SystemConfiguration resolved2 = resolver.resolve();
    assertTrue(resolved2.ssrf().isBypassHost("localhost"));
    assertFalse(resolved2.ssrf().isBypassHost("new-host"));

    // After invalidation, returns new value
    resolver.invalidateCache();
    SystemConfiguration resolved3 = resolver.resolve();
    assertFalse(resolved3.ssrf().isBypassHost("localhost"));
    assertTrue(resolved3.ssrf().isBypassHost("new-host"));
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
      // Ignore TTL for testing
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
  }

  /** Repository that counts how many times find() is called */
  private static class CountingRepository implements SystemConfigurationRepository {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private SystemConfiguration configuration = SystemConfiguration.defaultConfiguration();

    @Override
    public SystemConfiguration find() {
      callCount.incrementAndGet();
      return configuration;
    }

    @Override
    public void register(SystemConfiguration configuration) {
      this.configuration = configuration;
    }

    void setConfiguration(SystemConfiguration configuration) {
      this.configuration = configuration;
    }

    int getCallCount() {
      return callCount.get();
    }
  }
}
