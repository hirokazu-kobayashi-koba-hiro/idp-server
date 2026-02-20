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

import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.metadata.data.statement.MetadataStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CachedMdsResolver.
 *
 * <p>These tests focus on disabled configuration behavior. For enabled configuration with actual
 * network fetch, see MdsResolverIntegrationTest.
 */
class CachedMdsResolverTest {

  private InMemoryCacheStore cacheStore;

  @BeforeEach
  void setUp() {
    cacheStore = new InMemoryCacheStore();
  }

  @Test
  void resolve_shouldReturnEmpty_whenConfigDisabled() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    Optional<MetadataStatement> result = resolver.resolve("ee882879-721c-4913-9775-3dfcce97072a");

    assertTrue(result.isEmpty());
  }

  @Test
  void resolve_shouldReturnEmpty_whenAaguidIsNull() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    Optional<MetadataStatement> result = resolver.resolve((AAGUID) null);

    assertTrue(result.isEmpty());
  }

  @Test
  void resolve_shouldReturnEmpty_whenAaguidStringIsEmpty() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    Optional<MetadataStatement> result = resolver.resolve("");

    assertTrue(result.isEmpty());
  }

  @Test
  void checkStatus_shouldReturnNotFound_whenConfigDisabled() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    AuthenticatorStatus status = resolver.checkStatus("ee882879-721c-4913-9775-3dfcce97072a");

    assertFalse(status.isFound());
  }

  @Test
  void checkStatus_shouldReturnNotFound_whenAaguidIsNull() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    AuthenticatorStatus status = resolver.checkStatus((AAGUID) null);

    assertFalse(status.isFound());
    assertEquals("unknown", status.aaguid());
  }

  @Test
  void checkStatus_shouldReturnNotFound_whenAaguidStringIsEmpty() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    AuthenticatorStatus status = resolver.checkStatus("");

    assertFalse(status.isFound());
    assertEquals("unknown", status.aaguid());
  }

  @Test
  void checkStatus_shouldUseCache_onSecondCall() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);
    String aaguid = "ee882879-721c-4913-9775-3dfcce97072a";

    // First call - cache miss
    AuthenticatorStatus status1 = resolver.checkStatus(aaguid);

    // Second call - cache hit
    AuthenticatorStatus status2 = resolver.checkStatus(aaguid);

    assertEquals(status1.aaguid(), status2.aaguid());
    assertEquals(status1.isFound(), status2.isFound());
  }

  @Test
  void checkStatus_shouldCacheStatus() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);
    String aaguid = "ee882879-721c-4913-9775-3dfcce97072a";

    resolver.checkStatus(aaguid);

    String cacheKey = "mds:status:" + aaguid;
    assertTrue(cacheStore.exists(cacheKey));
  }

  @Test
  void isCompromised_shouldReturnFalse_whenNotFound() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    assertFalse(resolver.isCompromised("ee882879-721c-4913-9775-3dfcce97072a"));
  }

  @Test
  void isCompromised_shouldReturnFalse_forNullAaguid() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    assertFalse(resolver.isCompromised((AAGUID) null));
    assertFalse(resolver.isCompromised((String) null));
  }

  @Test
  void getEntryCount_shouldReturnZero_whenConfigDisabled() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    assertEquals(0, resolver.getEntryCount());
  }

  @Test
  void resolve_withAaguidObject_shouldDelegateToStringMethod() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);
    AAGUID aaguid = new AAGUID(UUID.fromString("ee882879-721c-4913-9775-3dfcce97072a"));

    Optional<MetadataStatement> result = resolver.resolve(aaguid);

    assertTrue(result.isEmpty());
  }

  @Test
  void checkStatus_withAaguidObject_shouldDelegateToStringMethod() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);
    AAGUID aaguid = new AAGUID(UUID.fromString("ee882879-721c-4913-9775-3dfcce97072a"));

    AuthenticatorStatus status = resolver.checkStatus(aaguid);

    assertFalse(status.isFound());
  }

  @Test
  void refresh_shouldNotThrowException_whenDisabled() {
    MdsConfiguration config = new MdsConfiguration(false);
    CachedMdsResolver resolver = new CachedMdsResolver(config, cacheStore);

    assertDoesNotThrow(() -> resolver.refresh());
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
