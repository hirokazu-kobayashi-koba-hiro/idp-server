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

package org.idp.server.platform.datasource.session;

import java.util.Optional;
import java.util.Set;

/**
 * SessionStore
 *
 * <p>A storage abstraction for OIDC session management. Unlike CacheStore which is for general
 * caching, SessionStore provides additional operations required for session management:
 *
 * <ul>
 *   <li>Set operations for indexing (e.g., opSession → clientSessions mapping)
 *   <li>TTL operations for session expiration
 * </ul>
 *
 * <p>Implementations may use Redis, in-memory storage, or other backends.
 */
public interface SessionStore {

  // ==================== Basic KV Operations ====================

  /**
   * Sets a value with TTL.
   *
   * @param key the key
   * @param value the value
   * @param ttlSeconds time-to-live in seconds (0 or negative means no expiration)
   */
  void set(String key, String value, long ttlSeconds);

  /**
   * Gets a value by key.
   *
   * @param key the key
   * @return the value, or empty if not found
   */
  Optional<String> get(String key);

  /**
   * Deletes one or more keys.
   *
   * @param keys the keys to delete
   */
  void delete(String... keys);

  // ==================== Set Operations ====================

  /**
   * Adds a member to a set.
   *
   * @param key the set key
   * @param member the member to add
   */
  void setAdd(String key, String member);

  /**
   * Removes a member from a set.
   *
   * @param key the set key
   * @param member the member to remove
   */
  void setRemove(String key, String member);

  /**
   * Gets all members of a set.
   *
   * @param key the set key
   * @return the set members, or empty set if not found
   */
  Set<String> setMembers(String key);

  // ==================== TTL Operations ====================

  /**
   * Sets the expiration time on a key.
   *
   * @param key the key
   * @param ttlSeconds time-to-live in seconds
   */
  void expire(String key, long ttlSeconds);

  /**
   * Gets the remaining TTL of a key.
   *
   * @param key the key
   * @return the remaining TTL in seconds, -1 if no expiration, -2 if key doesn't exist
   */
  long ttl(String key);

  // ==================== Configuration ====================

}
