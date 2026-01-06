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

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.idp.server.platform.datasource.session.SessionConfiguration;
import org.idp.server.platform.datasource.session.SessionStore;
import org.idp.server.platform.datasource.session.SessionStoreException;
import org.idp.server.platform.log.LoggerWrapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * JedisSessionStore
 *
 * <p>Redis implementation of SessionStore using Jedis client.
 */
public class JedisSessionStore implements SessionStore {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(JedisSessionStore.class);

  private final JedisPool jedisPool;

  public JedisSessionStore(SessionConfiguration config) {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(config.maxTotal());
    poolConfig.setMaxIdle(config.maxIdle());
    poolConfig.setMinIdle(config.minIdle());

    if (config.hasPassword()) {
      this.jedisPool =
          new JedisPool(
              poolConfig,
              config.host(),
              config.port(),
              config.timeout(),
              config.password(),
              config.database());
    } else {
      this.jedisPool =
          new JedisPool(
              poolConfig, config.host(), config.port(), config.timeout(), null, config.database());
    }
    log.info("JedisSessionStore initialized with Redis at {}:{}", config.host(), config.port());
  }

  @Override
  public void set(String key, String value, long ttlSeconds) {
    try (Jedis jedis = jedisPool.getResource()) {
      if (ttlSeconds > 0) {
        jedis.setex(key, ttlSeconds, value);
      } else {
        jedis.set(key, value);
      }
    } catch (Exception e) {
      log.error("Failed to set key: {}", key, e);
      throw new SessionStoreException("Failed to set session data", e);
    }
  }

  @Override
  public Optional<String> get(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      String value = jedis.get(key);
      return Optional.ofNullable(value);
    } catch (Exception e) {
      log.error("Failed to get key: {}", key, e);
      return Optional.empty();
    }
  }

  @Override
  public void delete(String... keys) {
    if (keys == null || keys.length == 0) {
      return;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.del(keys);
    } catch (Exception e) {
      log.error("Failed to delete keys", e);
      throw new SessionStoreException("Failed to delete session data", e);
    }
  }

  @Override
  public void setAdd(String key, String member) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.sadd(key, member);
    } catch (Exception e) {
      log.error("Failed to add to set: {}", key, e);
      throw new SessionStoreException("Failed to add to session index", e);
    }
  }

  @Override
  public void setRemove(String key, String member) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.srem(key, member);
    } catch (Exception e) {
      log.error("Failed to remove from set: {}", key, e);
      throw new SessionStoreException("Failed to remove from session index", e);
    }
  }

  @Override
  public Set<String> setMembers(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> members = jedis.smembers(key);
      return members != null ? members : Collections.emptySet();
    } catch (Exception e) {
      log.error("Failed to get set members: {}", key, e);
      return Collections.emptySet();
    }
  }

  @Override
  public void expire(String key, long ttlSeconds) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.expire(key, ttlSeconds);
    } catch (Exception e) {
      log.error("Failed to set expiration: {}", key, e);
      throw new SessionStoreException("Failed to set session expiration", e);
    }
  }

  @Override
  public long ttl(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.ttl(key);
    } catch (Exception e) {
      log.error("Failed to get TTL: {}", key, e);
      return -2;
    }
  }
}
