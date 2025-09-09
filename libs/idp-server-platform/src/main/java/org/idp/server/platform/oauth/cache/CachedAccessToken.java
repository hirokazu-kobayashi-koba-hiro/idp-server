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

import org.idp.server.platform.oauth.OAuthAuthorizationConfiguration;

public class CachedAccessToken {

  private String accessToken;
  private long expirationTimestamp;
  private int bufferSeconds;

  public CachedAccessToken() {}

  public CachedAccessToken(String accessToken, int expiresIn, int bufferSeconds) {
    this.accessToken = accessToken;
    this.bufferSeconds = bufferSeconds;
    this.expirationTimestamp = System.currentTimeMillis() + (expiresIn * 1000L);
  }

  public String getAccessToken() {
    return accessToken;
  }

  public long getExpirationTimestamp() {
    return expirationTimestamp;
  }

  public int getBufferSeconds() {
    return bufferSeconds;
  }

  public boolean isValid() {
    long currentTime = System.currentTimeMillis();
    long bufferTime = bufferSeconds * 1000L;
    return currentTime < (expirationTimestamp - bufferTime);
  }

  public static String generateCacheKey(OAuthAuthorizationConfiguration config) {
    String endpoint = config.tokenEndpoint() != null ? config.tokenEndpoint() : "";
    String clientId = config.clientId() != null ? config.clientId() : "";
    String scope = config.scope() != null ? config.scope() : "";
    String type = config.type() != null ? config.type() : "";
    String username = config.username() != null ? config.username() : "";

    // Create human-readable cache key for easier debugging and log analysis
    StringBuilder keyBuilder = new StringBuilder("oauth_token");

    keyBuilder.append(":type=").append(type);
    keyBuilder.append(":client=").append(sanitizeForKey(clientId));

    if (!scope.isEmpty()) {
      keyBuilder.append(":scope=").append(sanitizeForKey(scope));
    }

    if (!username.isEmpty()) {
      keyBuilder.append(":user=").append(sanitizeForKey(username));
    }

    keyBuilder.append(":endpoint=").append(endpoint);

    return keyBuilder.toString();
  }

  private static String sanitizeForKey(String input) {
    if (input == null || input.isEmpty()) {
      return "";
    }
    // Replace problematic characters for cache keys and limit length
    String sanitized = input.replaceAll("[^a-zA-Z0-9._-]", "_");
    return sanitized.length() > 50 ? sanitized.substring(0, 50) : sanitized;
  }

  public long getRemainingTimeSeconds() {
    long currentTime = System.currentTimeMillis();
    long bufferTime = bufferSeconds * 1000L;
    long remainingTime = (expirationTimestamp - bufferTime) - currentTime;
    return Math.max(0, remainingTime / 1000);
  }
}
