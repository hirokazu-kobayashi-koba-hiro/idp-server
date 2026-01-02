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

import java.util.Optional;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisOPSessionDataSource implements OPSessionRepository {

  private static final String KEY_PREFIX = "op_session:";
  private static final String BROWSER_STATE_PREFIX = "browser_state:";

  private final JedisPool jedisPool;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private final LoggerWrapper log = LoggerWrapper.getLogger(RedisOPSessionDataSource.class);

  public RedisOPSessionDataSource(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public void save(Tenant tenant, OPSession session) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = buildKey(tenant, session.id());
      String json = jsonConverter.write(session);
      long ttl = session.ttlSeconds();

      if (ttl > 0) {
        jedis.setex(key, ttl, json);
      } else {
        jedis.set(key, json);
      }

      // browser_state も保存
      if (session.browserState() != null && session.browserState().exists()) {
        String browserStateKey = buildBrowserStateKey(tenant, session.id());
        if (ttl > 0) {
          jedis.setex(browserStateKey, ttl, session.browserState().value());
        } else {
          jedis.set(browserStateKey, session.browserState().value());
        }
      }

      log.debug(
          "Saved OP session. id:{}, tenant:{}, ttl:{}",
          session.id().value(),
          tenant.identifierValue(),
          ttl);
    } catch (Exception e) {
      log.error("Failed to save OP session", e);
      throw new RuntimeException("Failed to save OP session", e);
    }
  }

  @Override
  public Optional<OPSession> findById(Tenant tenant, OPSessionIdentifier id) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = buildKey(tenant, id);
      String json = jedis.get(key);

      if (json == null) {
        return Optional.empty();
      }

      OPSession session = jsonConverter.read(json, OPSession.class);
      log.debug("Found OP session. id:{}, tenant:{}", id.value(), tenant.identifierValue());
      return Optional.of(session);
    } catch (Exception e) {
      log.error("Failed to find OP session", e);
      return Optional.empty();
    }
  }

  @Override
  public void delete(Tenant tenant, OPSessionIdentifier id) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = buildKey(tenant, id);
      String browserStateKey = buildBrowserStateKey(tenant, id);

      jedis.del(key, browserStateKey);
      log.debug("Deleted OP session. id:{}, tenant:{}", id.value(), tenant.identifierValue());
    } catch (Exception e) {
      log.error("Failed to delete OP session", e);
      throw new RuntimeException("Failed to delete OP session", e);
    }
  }

  @Override
  public void updateLastAccessedAt(Tenant tenant, OPSession session) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = buildKey(tenant, session.id());
      String json = jsonConverter.write(session);
      long ttl = jedis.ttl(key);

      if (ttl > 0) {
        jedis.setex(key, ttl, json);
      } else {
        jedis.set(key, json);
      }

      log.debug(
          "Updated OP session lastAccessedAt. id:{}, tenant:{}",
          session.id().value(),
          tenant.identifierValue());
    } catch (Exception e) {
      log.error("Failed to update OP session", e);
    }
  }

  private String buildKey(Tenant tenant, OPSessionIdentifier id) {
    return KEY_PREFIX + tenant.identifierValue() + ":" + id.value();
  }

  private String buildBrowserStateKey(Tenant tenant, OPSessionIdentifier id) {
    return BROWSER_STATE_PREFIX + tenant.identifierValue() + ":" + id.value();
  }
}
