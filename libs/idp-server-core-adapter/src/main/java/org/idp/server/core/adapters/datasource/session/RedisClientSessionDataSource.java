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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.idp.server.core.openid.session.ClientSession;
import org.idp.server.core.openid.session.ClientSessionIdentifier;
import org.idp.server.core.openid.session.ClientSessions;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.core.openid.session.repository.ClientSessionRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisClientSessionDataSource implements ClientSessionRepository {

  private static final String KEY_PREFIX = "client_session:";
  private static final String IDX_OP_SESSION_PREFIX = "idx:op_session:";
  private static final String IDX_TENANT_SUB_PREFIX = "idx:tenant:";

  private final JedisPool jedisPool;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private final LoggerWrapper log = LoggerWrapper.getLogger(RedisClientSessionDataSource.class);

  public RedisClientSessionDataSource(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public void save(Tenant tenant, ClientSession session) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = buildKey(tenant, session.sid());
      String json = jsonConverter.write(session);
      long ttl = session.ttlSeconds();

      // セッション本体を保存
      if (ttl > 0) {
        jedis.setex(key, ttl, json);
      } else {
        jedis.set(key, json);
      }

      // インデックス: OPセッション → クライアントセッション群
      String opSessionIndexKey = buildOpSessionIndexKey(tenant, session.opSessionId());
      jedis.sadd(opSessionIndexKey, session.sid().value());
      if (ttl > 0) {
        jedis.expire(opSessionIndexKey, ttl);
      }

      // インデックス: テナント+sub → クライアントセッション群
      String tenantSubIndexKey = buildTenantSubIndexKey(session.tenantId(), session.sub());
      jedis.sadd(tenantSubIndexKey, session.sid().value());
      if (ttl > 0) {
        jedis.expire(tenantSubIndexKey, ttl);
      }

      // インデックス: テナント+client+sub → クライアントセッション群
      String tenantClientSubIndexKey =
          buildTenantClientSubIndexKey(session.tenantId(), session.clientId(), session.sub());
      jedis.sadd(tenantClientSubIndexKey, session.sid().value());
      if (ttl > 0) {
        jedis.expire(tenantClientSubIndexKey, ttl);
      }

      log.debug(
          "Saved client session. sid:{}, opSessionId:{}, clientId:{}",
          session.sid().value(),
          session.opSessionId().value(),
          session.clientId());
    } catch (Exception e) {
      log.error("Failed to save client session", e);
      throw new RuntimeException("Failed to save client session", e);
    }
  }

  @Override
  public Optional<ClientSession> findBySid(Tenant tenant, ClientSessionIdentifier sid) {
    try (Jedis jedis = jedisPool.getResource()) {
      String key = buildKey(tenant, sid);
      String json = jedis.get(key);

      if (json == null) {
        return Optional.empty();
      }

      ClientSession session = jsonConverter.read(json, ClientSession.class);
      log.debug("Found client session. sid:{}, tenant:{}", sid.value(), tenant.identifierValue());
      return Optional.of(session);
    } catch (Exception e) {
      log.error("Failed to find client session", e);
      return Optional.empty();
    }
  }

  @Override
  public ClientSessions findByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId) {
    try (Jedis jedis = jedisPool.getResource()) {
      String indexKey = buildOpSessionIndexKey(tenant, opSessionId);
      Set<String> sids = jedis.smembers(indexKey);

      if (sids == null || sids.isEmpty()) {
        return ClientSessions.empty();
      }

      List<ClientSession> sessions = new ArrayList<>();
      for (String sid : sids) {
        String key = buildKey(tenant, new ClientSessionIdentifier(sid));
        String json = jedis.get(key);
        if (json != null) {
          sessions.add(jsonConverter.read(json, ClientSession.class));
        }
      }

      log.debug(
          "Found {} client sessions for opSessionId:{}, tenant:{}",
          sessions.size(),
          opSessionId.value(),
          tenant.identifierValue());
      return new ClientSessions(sessions);
    } catch (Exception e) {
      log.error("Failed to find client sessions by opSessionId", e);
      return ClientSessions.empty();
    }
  }

  @Override
  public ClientSessions findByTenantAndSub(TenantIdentifier tenantId, String sub) {
    try (Jedis jedis = jedisPool.getResource()) {
      String indexKey = buildTenantSubIndexKey(tenantId, sub);
      Set<String> sids = jedis.smembers(indexKey);

      if (sids == null || sids.isEmpty()) {
        return ClientSessions.empty();
      }

      List<ClientSession> sessions = new ArrayList<>();
      for (String sid : sids) {
        String key = buildKey(tenantId, new ClientSessionIdentifier(sid));
        String json = jedis.get(key);
        if (json != null) {
          sessions.add(jsonConverter.read(json, ClientSession.class));
        }
      }

      log.debug(
          "Found {} client sessions for tenant:{}, sub:{}", sessions.size(), tenantId.value(), sub);
      return new ClientSessions(sessions);
    } catch (Exception e) {
      log.error("Failed to find client sessions by tenant and sub", e);
      return ClientSessions.empty();
    }
  }

  @Override
  public ClientSessions findByTenantClientAndSub(
      TenantIdentifier tenantId, String clientId, String sub) {
    try (Jedis jedis = jedisPool.getResource()) {
      String indexKey = buildTenantClientSubIndexKey(tenantId, clientId, sub);
      Set<String> sids = jedis.smembers(indexKey);

      if (sids == null || sids.isEmpty()) {
        return ClientSessions.empty();
      }

      List<ClientSession> sessions = new ArrayList<>();
      for (String sid : sids) {
        String key = buildKey(tenantId, new ClientSessionIdentifier(sid));
        String json = jedis.get(key);
        if (json != null) {
          sessions.add(jsonConverter.read(json, ClientSession.class));
        }
      }

      log.debug(
          "Found {} client sessions for tenant:{}, clientId:{}, sub:{}",
          sessions.size(),
          tenantId.value(),
          clientId,
          sub);
      return new ClientSessions(sessions);
    } catch (Exception e) {
      log.error("Failed to find client sessions by tenant, client and sub", e);
      return ClientSessions.empty();
    }
  }

  @Override
  public void deleteBySid(Tenant tenant, ClientSessionIdentifier sid) {
    try (Jedis jedis = jedisPool.getResource()) {
      // まずセッション情報を取得してインデックスを削除
      String key = buildKey(tenant, sid);
      String json = jedis.get(key);

      if (json != null) {
        ClientSession session = jsonConverter.read(json, ClientSession.class);
        removeFromIndexes(jedis, tenant, session);
      }

      jedis.del(key);
      log.debug("Deleted client session. sid:{}, tenant:{}", sid.value(), tenant.identifierValue());
    } catch (Exception e) {
      log.error("Failed to delete client session", e);
      throw new RuntimeException("Failed to delete client session", e);
    }
  }

  @Override
  public int deleteByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId) {
    try (Jedis jedis = jedisPool.getResource()) {
      String indexKey = buildOpSessionIndexKey(tenant, opSessionId);
      Set<String> sids = jedis.smembers(indexKey);

      if (sids == null || sids.isEmpty()) {
        return 0;
      }

      int deletedCount = 0;
      for (String sid : sids) {
        String key = buildKey(tenant, new ClientSessionIdentifier(sid));
        String json = jedis.get(key);

        if (json != null) {
          ClientSession session = jsonConverter.read(json, ClientSession.class);
          removeFromIndexes(jedis, tenant, session);
          jedis.del(key);
          deletedCount++;
        }
      }

      jedis.del(indexKey);
      log.debug(
          "Deleted {} client sessions for opSessionId:{}, tenant:{}",
          deletedCount,
          opSessionId.value(),
          tenant.identifierValue());
      return deletedCount;
    } catch (Exception e) {
      log.error("Failed to delete client sessions by opSessionId", e);
      throw new RuntimeException("Failed to delete client sessions", e);
    }
  }

  private void removeFromIndexes(Jedis jedis, Tenant tenant, ClientSession session) {
    String opSessionIndexKey = buildOpSessionIndexKey(tenant, session.opSessionId());
    jedis.srem(opSessionIndexKey, session.sid().value());

    String tenantSubIndexKey = buildTenantSubIndexKey(session.tenantId(), session.sub());
    jedis.srem(tenantSubIndexKey, session.sid().value());

    String tenantClientSubIndexKey =
        buildTenantClientSubIndexKey(session.tenantId(), session.clientId(), session.sub());
    jedis.srem(tenantClientSubIndexKey, session.sid().value());
  }

  private String buildKey(Tenant tenant, ClientSessionIdentifier sid) {
    return KEY_PREFIX + tenant.identifierValue() + ":" + sid.value();
  }

  private String buildKey(TenantIdentifier tenantId, ClientSessionIdentifier sid) {
    return KEY_PREFIX + tenantId.value() + ":" + sid.value();
  }

  private String buildOpSessionIndexKey(Tenant tenant, OPSessionIdentifier opSessionId) {
    return IDX_OP_SESSION_PREFIX + tenant.identifierValue() + ":" + opSessionId.value();
  }

  private String buildTenantSubIndexKey(TenantIdentifier tenantId, String sub) {
    return IDX_TENANT_SUB_PREFIX + tenantId.value() + ":sub:" + sub;
  }

  private String buildTenantClientSubIndexKey(
      TenantIdentifier tenantId, String clientId, String sub) {
    return IDX_TENANT_SUB_PREFIX + tenantId.value() + ":client:" + clientId + ":sub:" + sub;
  }
}
