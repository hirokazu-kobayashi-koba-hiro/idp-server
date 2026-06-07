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

package org.idp.server.platform.datasource.cache;

import java.util.Optional;

public interface CacheStore {
  <T> void put(String key, T value);

  <T> void put(String key, T value, int timeToLiveSeconds);

  <T> Optional<T> find(String key, Class<T> type);

  boolean exists(String key);

  void delete(String key);

  /**
   * Delete all cache entries whose key starts with the given prefix.
   *
   * <p>Useful for invalidating a logical group of entries when a per-key invalidation could leave
   * stale values behind (e.g. when the field used as part of the cache key is itself mutable).
   *
   * <p>Implementations should make this safe to call on Redis-backed stores by using a cursor-based
   * scan rather than {@code KEYS *}.
   */
  void deleteByPrefix(String prefix);

  long increment(String key, int timeToLiveSeconds);
}
