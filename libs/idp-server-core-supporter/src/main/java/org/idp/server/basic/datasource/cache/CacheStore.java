package org.idp.server.basic.datasource.cache;

import java.util.Optional;

public interface CacheStore {
  <T> void put(String key, T value, int ttl);

  <T> Optional<T> find(String key, Class<T> type);

  void delete(String key);
}
