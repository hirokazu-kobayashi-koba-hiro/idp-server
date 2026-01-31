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
   * Atomically increment a counter by the given delta.
   *
   * <p>Uses Redis INCRBY command for atomic increment operations. This is useful for counters that
   * need to be updated from multiple instances without race conditions.
   *
   * @param key the key to increment
   * @param delta the amount to add (can be negative for decrement)
   * @return the value after the increment
   */
  long incrementBy(String key, long delta);

  /**
   * Atomically increment a counter by the given delta with TTL.
   *
   * <p>Sets TTL only when the key is newly created (when result equals delta).
   *
   * @param key the key to increment
   * @param delta the amount to add (can be negative for decrement)
   * @param timeToLiveSeconds the TTL for newly created keys
   * @return the value after the increment
   */
  long incrementBy(String key, long delta, int timeToLiveSeconds);

  /**
   * Find all keys matching the given pattern.
   *
   * <p>Uses Redis SCAN command for safe iteration over large key sets. Pattern supports glob-style
   * wildcards: * matches any sequence of characters, ? matches any single character.
   *
   * @param pattern the glob-style pattern to match (e.g., "statistics:*")
   * @return set of matching keys
   */
  java.util.Set<String> keys(String pattern);

  /**
   * Atomically get and delete a key's value.
   *
   * <p>Uses Redis GETDEL command (Redis 6.2+) or Lua script for atomicity. Returns 0 if the key
   * does not exist.
   *
   * @param key the key to get and delete
   * @return the value before deletion, or 0 if the key did not exist
   */
  long getAndDelete(String key);
}
