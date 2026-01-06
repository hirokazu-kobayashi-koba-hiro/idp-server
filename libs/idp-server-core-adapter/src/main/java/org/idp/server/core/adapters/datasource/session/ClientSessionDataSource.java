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
import org.idp.server.platform.datasource.session.SessionStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * ClientSessionDataSource
 *
 * <p>Implementation of ClientSessionRepository using SessionStore abstraction. Works with both
 * Redis and InMemory backends depending on the SessionStore implementation.
 *
 * <p>This implementation uses graceful degradation - session store errors are logged but not
 * propagated. This ensures that core authentication flows can continue even if the session store
 * (e.g., Redis) is unavailable.
 */
public class ClientSessionDataSource implements ClientSessionRepository {

  private static final String KEY_PREFIX = "client_session:";
  private static final String IDX_OP_SESSION_PREFIX = "idx:op_session:";
  private static final String IDX_TENANT_SUB_PREFIX = "idx:tenant:";

  private final SessionStore sessionStore;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private final LoggerWrapper log = LoggerWrapper.getLogger(ClientSessionDataSource.class);

  public ClientSessionDataSource(SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public void register(Tenant tenant, ClientSession session) {
    try {
      String key = buildKey(tenant, session.sid());
      String json = jsonConverter.write(session);
      long ttl = session.ttlSeconds();

      // Save session data
      sessionStore.set(key, json, ttl);

      // Index: OP session -> client sessions
      String opSessionIndexKey = buildOpSessionIndexKey(tenant, session.opSessionId());
      sessionStore.setAdd(opSessionIndexKey, session.sid().value());
      if (ttl > 0) {
        sessionStore.expire(opSessionIndexKey, ttl);
      }

      // Index: tenant + sub -> client sessions
      String tenantSubIndexKey = buildTenantSubIndexKey(session.tenantId(), session.sub());
      sessionStore.setAdd(tenantSubIndexKey, session.sid().value());
      if (ttl > 0) {
        sessionStore.expire(tenantSubIndexKey, ttl);
      }

      // Index: tenant + client + sub -> client sessions
      String tenantClientSubIndexKey =
          buildTenantClientSubIndexKey(session.tenantId(), session.clientId(), session.sub());
      sessionStore.setAdd(tenantClientSubIndexKey, session.sid().value());
      if (ttl > 0) {
        sessionStore.expire(tenantClientSubIndexKey, ttl);
      }

      log.debug(
          "Saved client session. sid:{}, opSessionId:{}, clientId:{}",
          session.sid().value(),
          session.opSessionId().value(),
          session.clientId());
    } catch (Exception e) {
      log.error(
          "Failed to save client session (graceful degradation). sid:{}, error:{}",
          session.sid().value(),
          e.getMessage());
    }
  }

  @Override
  public void update(Tenant tenant, ClientSession session) {
    try {
      String key = buildKey(tenant, session.sid());
      String json = jsonConverter.write(session);
      long ttl = session.ttlSeconds();

      // Update session data (indexes remain unchanged as sid doesn't change)
      sessionStore.set(key, json, ttl);

      // Update TTL on indexes
      String opSessionIndexKey = buildOpSessionIndexKey(tenant, session.opSessionId());
      if (ttl > 0) {
        sessionStore.expire(opSessionIndexKey, ttl);
      }

      String tenantSubIndexKey = buildTenantSubIndexKey(session.tenantId(), session.sub());
      if (ttl > 0) {
        sessionStore.expire(tenantSubIndexKey, ttl);
      }

      String tenantClientSubIndexKey =
          buildTenantClientSubIndexKey(session.tenantId(), session.clientId(), session.sub());
      if (ttl > 0) {
        sessionStore.expire(tenantClientSubIndexKey, ttl);
      }

      log.debug(
          "Updated client session. sid:{}, opSessionId:{}, clientId:{}",
          session.sid().value(),
          session.opSessionId().value(),
          session.clientId());
    } catch (Exception e) {
      log.error(
          "Failed to update client session (graceful degradation). sid:{}, error:{}",
          session.sid().value(),
          e.getMessage());
    }
  }

  @Override
  public Optional<ClientSession> findBySid(Tenant tenant, ClientSessionIdentifier sid) {
    try {
      String key = buildKey(tenant, sid);
      return sessionStore
          .get(key)
          .map(
              json -> {
                ClientSession session = jsonConverter.read(json, ClientSession.class);
                log.debug(
                    "Found client session. sid:{}, tenant:{}",
                    sid.value(),
                    tenant.identifierValue());
                return session;
              });
    } catch (Exception e) {
      log.error(
          "Failed to find client session (graceful degradation). sid:{}, error:{}",
          sid.value(),
          e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public ClientSessions findByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId) {
    try {
      String indexKey = buildOpSessionIndexKey(tenant, opSessionId);
      Set<String> sids = sessionStore.setMembers(indexKey);

      if (sids.isEmpty()) {
        return ClientSessions.empty();
      }

      List<ClientSession> sessions = new ArrayList<>();
      for (String sid : sids) {
        String key = buildKey(tenant, new ClientSessionIdentifier(sid));
        sessionStore
            .get(key)
            .ifPresent(json -> sessions.add(jsonConverter.read(json, ClientSession.class)));
      }

      log.debug(
          "Found {} client sessions for opSessionId:{}, tenant:{}",
          sessions.size(),
          opSessionId.value(),
          tenant.identifierValue());
      return new ClientSessions(sessions);
    } catch (Exception e) {
      log.error(
          "Failed to find client sessions by opSessionId (graceful degradation). opSessionId:{}, error:{}",
          opSessionId.value(),
          e.getMessage());
      return ClientSessions.empty();
    }
  }

  @Override
  public Optional<ClientSession> findByOpSessionIdAndClientId(
      Tenant tenant, OPSessionIdentifier opSessionId, String clientId) {
    try {
      ClientSessions sessions = findByOpSessionId(tenant, opSessionId);
      Optional<ClientSession> result =
          sessions.stream().filter(session -> clientId.equals(session.clientId())).findFirst();

      result.ifPresent(
          session ->
              log.debug(
                  "Found client session for opSessionId:{}, clientId:{}, sid:{}",
                  opSessionId.value(),
                  clientId,
                  session.sid().value()));

      return result;
    } catch (Exception e) {
      log.error(
          "Failed to find client session by opSessionId and clientId (graceful degradation). opSessionId:{}, clientId:{}, error:{}",
          opSessionId.value(),
          clientId,
          e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public ClientSessions findByTenantAndSub(TenantIdentifier tenantId, String sub) {
    try {
      String indexKey = buildTenantSubIndexKey(tenantId, sub);
      Set<String> sids = sessionStore.setMembers(indexKey);

      if (sids.isEmpty()) {
        return ClientSessions.empty();
      }

      List<ClientSession> sessions = new ArrayList<>();
      for (String sid : sids) {
        String key = buildKey(tenantId, new ClientSessionIdentifier(sid));
        sessionStore
            .get(key)
            .ifPresent(json -> sessions.add(jsonConverter.read(json, ClientSession.class)));
      }

      log.debug(
          "Found {} client sessions for tenant:{}, sub:{}", sessions.size(), tenantId.value(), sub);
      return new ClientSessions(sessions);
    } catch (Exception e) {
      log.error(
          "Failed to find client sessions by tenant and sub (graceful degradation). tenant:{}, sub:{}, error:{}",
          tenantId.value(),
          sub,
          e.getMessage());
      return ClientSessions.empty();
    }
  }

  @Override
  public ClientSessions findByTenantClientAndSub(
      TenantIdentifier tenantId, String clientId, String sub) {
    try {
      String indexKey = buildTenantClientSubIndexKey(tenantId, clientId, sub);
      Set<String> sids = sessionStore.setMembers(indexKey);

      if (sids.isEmpty()) {
        return ClientSessions.empty();
      }

      List<ClientSession> sessions = new ArrayList<>();
      for (String sid : sids) {
        String key = buildKey(tenantId, new ClientSessionIdentifier(sid));
        sessionStore
            .get(key)
            .ifPresent(json -> sessions.add(jsonConverter.read(json, ClientSession.class)));
      }

      log.debug(
          "Found {} client sessions for tenant:{}, clientId:{}, sub:{}",
          sessions.size(),
          tenantId.value(),
          clientId,
          sub);
      return new ClientSessions(sessions);
    } catch (Exception e) {
      log.error(
          "Failed to find client sessions by tenant, client and sub (graceful degradation). tenant:{}, clientId:{}, sub:{}, error:{}",
          tenantId.value(),
          clientId,
          sub,
          e.getMessage());
      return ClientSessions.empty();
    }
  }

  @Override
  public void deleteBySid(Tenant tenant, ClientSessionIdentifier sid) {
    try {
      String key = buildKey(tenant, sid);

      // First get session info to remove from indexes
      sessionStore
          .get(key)
          .ifPresent(
              json -> {
                ClientSession session = jsonConverter.read(json, ClientSession.class);
                removeFromIndexes(tenant, session);
              });

      sessionStore.delete(key);
      log.debug("Deleted client session. sid:{}, tenant:{}", sid.value(), tenant.identifierValue());
    } catch (Exception e) {
      log.error(
          "Failed to delete client session (graceful degradation). sid:{}, error:{}",
          sid.value(),
          e.getMessage());
    }
  }

  @Override
  public int deleteByOpSessionId(Tenant tenant, OPSessionIdentifier opSessionId) {
    try {
      String indexKey = buildOpSessionIndexKey(tenant, opSessionId);
      Set<String> sids = sessionStore.setMembers(indexKey);

      if (sids.isEmpty()) {
        return 0;
      }

      int deletedCount = 0;
      for (String sid : sids) {
        String key = buildKey(tenant, new ClientSessionIdentifier(sid));
        Optional<String> jsonOpt = sessionStore.get(key);

        if (jsonOpt.isPresent()) {
          ClientSession session = jsonConverter.read(jsonOpt.get(), ClientSession.class);
          removeFromIndexes(tenant, session);
          sessionStore.delete(key);
          deletedCount++;
        }
      }

      sessionStore.delete(indexKey);
      log.debug(
          "Deleted {} client sessions for opSessionId:{}, tenant:{}",
          deletedCount,
          opSessionId.value(),
          tenant.identifierValue());
      return deletedCount;
    } catch (Exception e) {
      log.error(
          "Failed to delete client sessions by opSessionId (graceful degradation). opSessionId:{}, error:{}",
          opSessionId.value(),
          e.getMessage());
      return 0;
    }
  }

  private void removeFromIndexes(Tenant tenant, ClientSession session) {
    String opSessionIndexKey = buildOpSessionIndexKey(tenant, session.opSessionId());
    sessionStore.setRemove(opSessionIndexKey, session.sid().value());

    String tenantSubIndexKey = buildTenantSubIndexKey(session.tenantId(), session.sub());
    sessionStore.setRemove(tenantSubIndexKey, session.sid().value());

    String tenantClientSubIndexKey =
        buildTenantClientSubIndexKey(session.tenantId(), session.clientId(), session.sub());
    sessionStore.setRemove(tenantClientSubIndexKey, session.sid().value());
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
