/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.cache;

import java.util.Optional;
import org.idp.server.platform.datasource.cache.CacheStore;

public class NoOperationCacheStore implements CacheStore {

  @Override
  public <T> void put(String key, T value) {}

  @Override
  public <T> Optional<T> find(String key, Class<T> type) {
    return Optional.empty();
  }

  @Override
  public void delete(String key) {}
}
