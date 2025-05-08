package org.idp.server.core.adapters.datasource.cache;

import java.util.Optional;
import org.idp.server.basic.datasource.cache.CacheConfiguration;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.log.LoggerWrapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisCacheStore implements CacheStore {

  JedisPool jedisPool;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  LoggerWrapper log = LoggerWrapper.getLogger(JedisCacheStore.class);

  public JedisCacheStore(CacheConfiguration cacheConfiguration) {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(cacheConfiguration.maxTotal());
    config.setMaxIdle(cacheConfiguration.maxIdle());
    config.setMinIdle(cacheConfiguration.minIdle());
    this.jedisPool = new JedisPool(config, cacheConfiguration.host(), cacheConfiguration.port());
  }

  @Override
  public <T> void put(String key, T value, int second) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = jsonConverter.write(value);
      resource.setex(key, second, json);
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

      log.info("Find cache. key:{}, type:{}", key, type.getSimpleName());
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
