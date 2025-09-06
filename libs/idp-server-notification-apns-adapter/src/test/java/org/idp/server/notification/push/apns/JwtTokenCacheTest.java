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

package org.idp.server.notification.push.apns;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtTokenCacheTest {

  @Test
  void testTokenCacheNotExpired() {
    Instant futureExpiry = Instant.now().plusSeconds(3600); // 1 hour future
    JwtTokenCache cache = new JwtTokenCache("test-token", futureExpiry);

    assertFalse(cache.isExpired());
    assertEquals("test-token", cache.token());
  }

  @Test
  void testTokenCacheExpired() {
    Instant pastExpiry = Instant.now().minusSeconds(60); // 1 minute past
    JwtTokenCache cache = new JwtTokenCache("test-token", pastExpiry);

    assertTrue(cache.isExpired());
  }

  @Test
  void testTokenShouldRefresh() {
    // Token expires in 4 minutes (240 seconds) - should refresh (refresh threshold is 5 minutes)
    Instant soonExpiry = Instant.now().plusSeconds(240);
    JwtTokenCache cache = new JwtTokenCache("test-token", soonExpiry);

    assertTrue(cache.shouldRefresh());
  }

  @Test
  void testTokenShouldNotRefresh() {
    // Token expires in 10 minutes - should not refresh yet
    Instant laterExpiry = Instant.now().plusSeconds(600);
    JwtTokenCache cache = new JwtTokenCache("test-token", laterExpiry);

    assertFalse(cache.shouldRefresh());
  }
}
