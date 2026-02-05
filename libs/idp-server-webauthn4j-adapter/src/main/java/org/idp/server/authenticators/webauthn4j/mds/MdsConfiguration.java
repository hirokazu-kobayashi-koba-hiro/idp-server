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

import org.idp.server.platform.json.JsonReadable;

public class MdsConfiguration implements JsonReadable {

  private static final int DEFAULT_CACHE_TTL_SECONDS = 86400; // 24 hours

  boolean enabled;
  int cacheTtlSeconds;

  public MdsConfiguration() {
    this.enabled = false;
    this.cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;
  }

  public MdsConfiguration(boolean enabled) {
    this.enabled = enabled;
    this.cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;
  }

  public MdsConfiguration(boolean enabled, int cacheTtlSeconds) {
    this.enabled = enabled;
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  public boolean enabled() {
    return enabled;
  }

  public int cacheTtlSeconds() {
    return cacheTtlSeconds > 0 ? cacheTtlSeconds : DEFAULT_CACHE_TTL_SECONDS;
  }
}
