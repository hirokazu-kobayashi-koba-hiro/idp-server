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

package org.idp.server.core.openid.oauth.replay;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/** Issue #1893: a short-lived JWT goes through once. */
class JwtReplayDetectorTest {

  /** Holds keys like Redis SET NX does, ignoring the TTL. */
  static class NxCacheStore implements CacheStore {
    Map<String, Integer> keys = new HashMap<>();

    @Override
    public <T> void put(String key, T value) {}

    @Override
    public <T> void put(String key, T value, int timeToLiveSeconds) {}

    @Override
    public <T> Optional<T> find(String key, Class<T> type) {
      return Optional.empty();
    }

    @Override
    public boolean exists(String key) {
      return keys.containsKey(key);
    }

    @Override
    public void delete(String key) {}

    @Override
    public void deleteByPrefix(String prefix) {}

    @Override
    public long increment(String key, int timeToLiveSeconds) {
      return 0;
    }

    @Override
    public boolean putIfAbsent(String key, int timeToLiveSeconds) {
      return keys.putIfAbsent(key, timeToLiveSeconds) == null;
    }
  }

  static final Duration WINDOW = Duration.ofSeconds(60);

  @Test
  void letsAJwtThroughOnce() {
    JwtReplayDetector detector = new JwtReplayDetector(new NxCacheStore());

    assertTrue(firstUse(detector, tenant("t1"), "jkt-a", "jti-1"));
    assertFalse(firstUse(detector, tenant("t1"), "jkt-a", "jti-1"));
  }

  @Test
  void keepsKeysClientsAndTenantsApart() {
    JwtReplayDetector detector = new JwtReplayDetector(new NxCacheStore());
    assertTrue(firstUse(detector, tenant("t1"), "jkt-a", "jti-1"));

    // Another key cannot use up this one's jti, and neither can another tenant.
    assertTrue(firstUse(detector, tenant("t1"), "jkt-b", "jti-1"));
    assertTrue(firstUse(detector, tenant("t2"), "jkt-a", "jti-1"));
    assertTrue(
        detector.firstUse(
            tenant("t1"),
            JwtReplayKind.CLIENT_ATTESTATION_POP,
            "jkt-a",
            "jti-1",
            Instant.now(),
            WINDOW));
  }

  @Test
  void letsEverythingThroughWithoutACacheStore() {
    JwtReplayDetector detector = new JwtReplayDetector(new NoOperationCacheStore());

    assertTrue(firstUse(detector, tenant("t1"), "jkt-a", "jti-1"));
    assertTrue(firstUse(detector, tenant("t1"), "jkt-a", "jti-1"));
  }

  @Test
  void remembersAJwtUntilItsWindowCloses() {
    Instant now = Instant.now();

    assertEquals(60, JwtReplayDetector.timeToLiveSeconds(now.plus(WINDOW)), 1);
    // iat in the future (clock ahead): accepted for longer, so remembered for longer.
    assertEquals(90, JwtReplayDetector.timeToLiveSeconds(now.plusSeconds(30).plus(WINDOW)), 1);
    // At the very edge of the window: still remembered.
    assertEquals(1, JwtReplayDetector.timeToLiveSeconds(now.minusSeconds(60).plus(WINDOW)));
  }

  @Test
  void remembersAClientAssertionUntilItsExp() {
    Instant now = Instant.now();

    // RFC 7523 Section 3: kept for as long as the assertion is valid by its exp.
    assertEquals(300, JwtReplayDetector.timeToLiveSeconds(now.plusSeconds(300)), 1);
  }

  private static boolean firstUse(
      JwtReplayDetector detector, Tenant tenant, String binding, String jti) {
    return detector.firstUse(tenant, JwtReplayKind.DPOP_PROOF, binding, jti, Instant.now(), WINDOW);
  }

  static Tenant tenant(String name) {
    String id =
        java.util
            .UUID
            .nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString();
    return new Tenant(
        new TenantIdentifier(id),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }
}
