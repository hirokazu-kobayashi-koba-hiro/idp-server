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

public class OAuthTokenCacheConfiguration {

  private boolean enabled;
  private int bufferSeconds;
  private int defaultTtlSeconds;

  public OAuthTokenCacheConfiguration() {
    this.enabled = false;
    this.bufferSeconds = 30;
    this.defaultTtlSeconds = 3600;
  }

  public OAuthTokenCacheConfiguration(boolean enabled, int bufferSeconds, int defaultTtlSeconds) {
    this.enabled = enabled;
    this.bufferSeconds = bufferSeconds;
    this.defaultTtlSeconds = defaultTtlSeconds;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getBufferSeconds() {
    return bufferSeconds;
  }

  public void setBufferSeconds(int bufferSeconds) {
    this.bufferSeconds = bufferSeconds;
  }

  public int getDefaultTtlSeconds() {
    return defaultTtlSeconds;
  }

  public void setDefaultTtlSeconds(int defaultTtlSeconds) {
    this.defaultTtlSeconds = defaultTtlSeconds;
  }

  public static OAuthTokenCacheConfiguration disabled() {
    return new OAuthTokenCacheConfiguration(false, 30, 3600);
  }

  public static OAuthTokenCacheConfiguration enabled() {
    return new OAuthTokenCacheConfiguration(true, 30, 3600);
  }

  public static OAuthTokenCacheConfiguration enabled(int bufferSeconds, int defaultTtlSeconds) {
    return new OAuthTokenCacheConfiguration(true, bufferSeconds, defaultTtlSeconds);
  }
}
