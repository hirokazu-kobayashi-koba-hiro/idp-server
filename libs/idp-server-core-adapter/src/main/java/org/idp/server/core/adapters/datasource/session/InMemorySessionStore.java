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

package org.idp.server.core.adapters.datasource.session;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.idp.server.platform.datasource.session.SessionStore;

/**
 * InMemorySessionStore
 *
 * <p>In-memory implementation of SessionStore for testing and development. NOT suitable for
 * production use in clustered environments as data is not shared across instances.
 */
public class InMemorySessionStore implements SessionStore {

  private final Map<String, Entry> store = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();
  private final Map<String, Instant> setExpirations = new ConcurrentHashMap<>();

  @Override
  public void set(String key, String value, long ttlSeconds) {
    Instant expiresAt = ttlSeconds > 0 ? Instant.now().plusSeconds(ttlSeconds) : null;
    store.put(key, new Entry(value, expiresAt));
  }

  @Override
  public Optional<String> get(String key) {
    Entry entry = store.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.isExpired()) {
      store.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.value());
  }

  @Override
  public void delete(String... keys) {
    if (keys == null) {
      return;
    }
    for (String key : keys) {
      store.remove(key);
      sets.remove(key);
      setExpirations.remove(key);
    }
  }

  @Override
  public void setAdd(String key, String member) {
    sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(member);
  }

  @Override
  public void setRemove(String key, String member) {
    Set<String> set = sets.get(key);
    if (set != null) {
      set.remove(member);
    }
  }

  @Override
  public Set<String> setMembers(String key) {
    // Check set expiration
    Instant expiration = setExpirations.get(key);
    if (expiration != null && Instant.now().isAfter(expiration)) {
      sets.remove(key);
      setExpirations.remove(key);
      return Collections.emptySet();
    }

    Set<String> set = sets.get(key);
    return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
  }

  @Override
  public void expire(String key, long ttlSeconds) {
    // For regular entries
    Entry entry = store.get(key);
    if (entry != null) {
      Instant expiresAt = ttlSeconds > 0 ? Instant.now().plusSeconds(ttlSeconds) : null;
      store.put(key, new Entry(entry.value(), expiresAt));
    }

    // For set entries
    if (sets.containsKey(key)) {
      if (ttlSeconds > 0) {
        setExpirations.put(key, Instant.now().plusSeconds(ttlSeconds));
      } else {
        setExpirations.remove(key);
      }
    }
  }

  @Override
  public long ttl(String key) {
    // Check regular entry
    Entry entry = store.get(key);
    if (entry != null) {
      if (entry.expiresAt() == null) {
        return -1; // No expiration
      }
      long remaining = entry.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();
      return Math.max(remaining, 0);
    }

    // Check set entry
    Instant expiration = setExpirations.get(key);
    if (expiration != null) {
      long remaining = expiration.getEpochSecond() - Instant.now().getEpochSecond();
      return Math.max(remaining, 0);
    }

    // Check if set exists without expiration
    if (sets.containsKey(key)) {
      return -1; // No expiration
    }

    return -2; // Key doesn't exist
  }

  /** Clears all data. Useful for testing. */
  public void clear() {
    store.clear();
    sets.clear();
    setExpirations.clear();
  }

  private record Entry(String value, Instant expiresAt) {
    boolean isExpired() {
      return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
  }
}
