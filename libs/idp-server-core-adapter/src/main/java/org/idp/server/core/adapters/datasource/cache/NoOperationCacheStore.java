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
