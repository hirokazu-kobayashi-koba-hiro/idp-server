package org.idp.server.platform.datasource.cache;

import java.util.Optional;

public interface CacheStore {
  <T> void put(String key, T value);

  <T> Optional<T> find(String key, Class<T> type);

  void delete(String key);
}
