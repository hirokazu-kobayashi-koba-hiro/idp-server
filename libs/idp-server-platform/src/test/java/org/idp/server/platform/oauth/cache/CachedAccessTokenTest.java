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

package org.idp.server.platform.oauth.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;
import org.junit.jupiter.api.Test;

class CachedAccessTokenTest {

  @Test
  void testTokenValidityWithBuffer() {
    String accessToken = "test-access-token";
    int expiresIn = 3600; // 1 hour
    int bufferSeconds = 30; // 30 second buffer

    CachedAccessToken cachedToken = new CachedAccessToken(accessToken, expiresIn, bufferSeconds);

    assertNotNull(cachedToken.getAccessToken());
    assertEquals(accessToken, cachedToken.getAccessToken());
    assertTrue(cachedToken.isValid()); // Should be valid immediately
  }

  @Test
  void testTokenExpiration() throws InterruptedException {
    String accessToken = "test-access-token";
    int expiresIn = 1; // 1 second
    int bufferSeconds = 0; // No buffer for testing

    CachedAccessToken cachedToken = new CachedAccessToken(accessToken, expiresIn, bufferSeconds);

    assertTrue(cachedToken.isValid());

    // Wait for expiration
    Thread.sleep(1100);

    assertFalse(cachedToken.isValid());
  }

  @Test
  void testCacheKeyGeneration() {
    OAuthAuthorizationConfiguration config =
        new OAuthAuthorizationConfiguration(
            "client_credentials",
            "https://token.example.com",
            "client_secret_basic",
            "test-client",
            "test-secret",
            "read write",
            null,
            null,
            null);

    String key1 = CachedAccessToken.generateCacheKey(config);
    String key2 = CachedAccessToken.generateCacheKey(config);

    assertEquals(key1, key2);
    assertNotNull(key1);
  }

  @Test
  void testDifferentConfigGeneratesDifferentKey() {
    OAuthAuthorizationConfiguration config1 =
        new OAuthAuthorizationConfiguration(
            "client_credentials",
            "https://token.example.com",
            "client_secret_basic",
            "client1",
            "secret1",
            "read",
            null,
            null,
            null);

    OAuthAuthorizationConfiguration config2 =
        new OAuthAuthorizationConfiguration(
            "client_credentials",
            "https://token.example.com",
            "client_secret_basic",
            "client2",
            "secret2",
            "read",
            null,
            null,
            null);

    String key1 = CachedAccessToken.generateCacheKey(config1);
    String key2 = CachedAccessToken.generateCacheKey(config2);

    assertNotEquals(key1, key2);
  }

  @Test
  void testRemainingTimeCalculation() {
    String accessToken = "test-access-token";
    int expiresIn = 3600; // 1 hour
    int bufferSeconds = 30; // 30 second buffer

    CachedAccessToken cachedToken = new CachedAccessToken(accessToken, expiresIn, bufferSeconds);

    long remainingTime = cachedToken.getRemainingTimeSeconds();
    // Should be close to 3570 seconds (3600 - 30 buffer)
    assertTrue(remainingTime > 3560 && remainingTime <= 3570);
  }
}
