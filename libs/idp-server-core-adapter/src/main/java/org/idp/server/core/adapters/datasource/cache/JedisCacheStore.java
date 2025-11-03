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

package org.idp.server.core.adapters.datasource.cache;

import java.util.Optional;
import org.idp.server.platform.datasource.cache.CacheConfiguration;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisCacheStore implements CacheStore {

  JedisPool jedisPool;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(JedisCacheStore.class);
  int timeToLiveSecond;

  public JedisCacheStore(CacheConfiguration cacheConfiguration) {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(cacheConfiguration.maxTotal());
    config.setMaxIdle(cacheConfiguration.maxIdle());
    config.setMinIdle(cacheConfiguration.minIdle());

    String password = cacheConfiguration.password();
    if (password != null && !password.isEmpty()) {
      this.jedisPool =
          new JedisPool(
              config,
              cacheConfiguration.host(),
              cacheConfiguration.port(),
              cacheConfiguration.timeout(),
              password,
              cacheConfiguration.database());
    } else {
      this.jedisPool =
          new JedisPool(
              config,
              cacheConfiguration.host(),
              cacheConfiguration.port(),
              cacheConfiguration.timeout(),
              null,
              cacheConfiguration.database());
    }
    this.timeToLiveSecond = cacheConfiguration.timeToLiveSeconds();
  }

  @Override
  public <T> void put(String key, T value) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = jsonConverter.write(value);
      resource.setex(key, timeToLiveSecond, json);
    } catch (Exception e) {
      log.error("Failed to put cache", e);
    }
  }

  @Override
  public <T> Optional<T> find(String key, Class<T> type) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = resource.get(key);

      if (json == null) {
        return Optional.empty();
      }

      log.debug("Find cache. key:{}, type:{}", key, type.getSimpleName());
      return Optional.of(jsonConverter.read(json, type));
    } catch (Exception e) {

      log.error("Failed to find cache", e);
      return Optional.empty();
    }
  }

  @Override
  public void delete(String key) {
    try (Jedis resource = jedisPool.getResource()) {
      resource.del(key);
    } catch (Exception e) {
      log.error("Failed to delete cache", e);
    }
  }
}
