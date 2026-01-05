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
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.session.OPSession;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.core.openid.session.OPSessions;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.platform.datasource.session.SessionStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OPSessionDataSource
 *
 * <p>Implementation of OPSessionRepository using SessionStore abstraction. Works with both Redis
 * and InMemory backends depending on the SessionStore implementation.
 *
 * <p>This implementation uses graceful degradation - session store errors are logged but not
 * propagated. This ensures that core authentication flows can continue even if the session store
 * (e.g., Redis) is unavailable. The trade-off is that SSO functionality will be degraded (users
 * will need to re-authenticate each time).
 */
public class OPSessionDataSource implements OPSessionRepository {

  private static final String KEY_PREFIX = "op_session:";
  private static final String BROWSER_STATE_PREFIX = "browser_state:";
  private static final String IDX_USER_PREFIX = "idx:op_user:";

  private final SessionStore sessionStore;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private final LoggerWrapper log = LoggerWrapper.getLogger(OPSessionDataSource.class);

  public OPSessionDataSource(SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public void register(Tenant tenant, OPSession session) {
    try {
      String key = buildKey(tenant, session.id());
      String json = jsonConverter.write(session);
      long ttl = session.ttlSeconds();

      sessionStore.set(key, json, ttl);

      // Save browser_state as well
      if (session.browserState() != null && session.browserState().exists()) {
        String browserStateKey = buildBrowserStateKey(tenant, session.id());
        sessionStore.set(browserStateKey, session.browserState().value(), ttl);
      }

      // Index: user (sub) -> OP sessions
      String sub = session.sub();
      if (sub != null && !sub.isEmpty()) {
        String userIndexKey = buildUserIndexKey(tenant, sub);
        sessionStore.setAdd(userIndexKey, session.id().value());
        if (ttl > 0) {
          sessionStore.expire(userIndexKey, ttl);
        }
      }

      log.debug(
          "Saved OP session. id:{}, tenant:{}, sub:{}, ttl:{}",
          session.id().value(),
          tenant.identifierValue(),
          sub,
          ttl);
    } catch (Exception e) {
      log.error(
          "Failed to save OP session (graceful degradation). id:{}, tenant:{}, error:{}",
          session.id().value(),
          tenant.identifierValue(),
          e.getMessage());
    }
  }

  @Override
  public Optional<OPSession> findById(Tenant tenant, OPSessionIdentifier id) {
    try {
      String key = buildKey(tenant, id);
      return sessionStore
          .get(key)
          .map(
              json -> {
                OPSession session = jsonConverter.read(json, OPSession.class);
                log.debug(
                    "Found OP session. id:{}, tenant:{}", id.value(), tenant.identifierValue());
                return session;
              });
    } catch (Exception e) {
      log.error(
          "Failed to find OP session (graceful degradation). id:{}, tenant:{}, error:{}",
          id.value(),
          tenant.identifierValue(),
          e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public OPSessions findByUser(Tenant tenant, UserIdentifier userIdentifier) {
    try {
      String sub = userIdentifier.value();
      String userIndexKey = buildUserIndexKey(tenant, sub);
      Set<String> sessionIds = sessionStore.setMembers(userIndexKey);

      if (sessionIds.isEmpty()) {
        return OPSessions.empty();
      }

      List<OPSession> sessions = new ArrayList<>();
      for (String sessionId : sessionIds) {
        String key = buildKey(tenant, new OPSessionIdentifier(sessionId));
        sessionStore
            .get(key)
            .ifPresent(json -> sessions.add(jsonConverter.read(json, OPSession.class)));
      }

      log.debug(
          "Found {} OP sessions for tenant:{}, user:{}",
          sessions.size(),
          tenant.identifierValue(),
          sub);
      return new OPSessions(sessions);
    } catch (Exception e) {
      log.error(
          "Failed to find OP sessions by user (graceful degradation). tenant:{}, user:{}, error:{}",
          tenant.identifierValue(),
          userIdentifier.value(),
          e.getMessage());
      return OPSessions.empty();
    }
  }

  @Override
  public void delete(Tenant tenant, OPSessionIdentifier id) {
    try {
      String key = buildKey(tenant, id);
      String browserStateKey = buildBrowserStateKey(tenant, id);

      // First get session to remove from user index
      sessionStore
          .get(key)
          .ifPresent(
              json -> {
                OPSession session = jsonConverter.read(json, OPSession.class);
                String sub = session.sub();
                if (sub != null && !sub.isEmpty()) {
                  String userIndexKey = buildUserIndexKey(tenant, sub);
                  sessionStore.setRemove(userIndexKey, id.value());
                }
              });

      sessionStore.delete(key, browserStateKey);
      log.debug("Deleted OP session. id:{}, tenant:{}", id.value(), tenant.identifierValue());
    } catch (Exception e) {
      log.error(
          "Failed to delete OP session (graceful degradation). id:{}, tenant:{}, error:{}",
          id.value(),
          tenant.identifierValue(),
          e.getMessage());
    }
  }

  @Override
  public void deleteByUser(Tenant tenant, UserIdentifier userIdentifier) {
    try {
      String sub = userIdentifier.value();
      String userIndexKey = buildUserIndexKey(tenant, sub);
      Set<String> sessionIds = sessionStore.setMembers(userIndexKey);

      if (sessionIds.isEmpty()) {
        log.debug("No OP sessions to delete for tenant:{}, user:{}", tenant.identifierValue(), sub);
        return;
      }

      for (String sessionId : sessionIds) {
        OPSessionIdentifier id = new OPSessionIdentifier(sessionId);
        String key = buildKey(tenant, id);
        String browserStateKey = buildBrowserStateKey(tenant, id);
        sessionStore.delete(key, browserStateKey);
      }

      // Delete the user index
      sessionStore.delete(userIndexKey);

      log.debug(
          "Deleted {} OP sessions for tenant:{}, user:{}",
          sessionIds.size(),
          tenant.identifierValue(),
          sub);
    } catch (Exception e) {
      log.error(
          "Failed to delete OP sessions by user (graceful degradation). tenant:{}, user:{}, error:{}",
          tenant.identifierValue(),
          userIdentifier.value(),
          e.getMessage());
    }
  }

  @Override
  public void updateLastAccessedAt(Tenant tenant, OPSession session) {
    try {
      String key = buildKey(tenant, session.id());
      String json = jsonConverter.write(session);
      long ttl = sessionStore.ttl(key);

      sessionStore.set(key, json, ttl > 0 ? ttl : 0);

      log.debug(
          "Updated OP session lastAccessedAt. id:{}, tenant:{}",
          session.id().value(),
          tenant.identifierValue());
    } catch (Exception e) {
      log.error(
          "Failed to update OP session lastAccessedAt (graceful degradation). id:{}, tenant:{}, error:{}",
          session.id().value(),
          tenant.identifierValue(),
          e.getMessage());
    }
  }

  private String buildKey(Tenant tenant, OPSessionIdentifier id) {
    return KEY_PREFIX + tenant.identifierValue() + ":" + id.value();
  }

  private String buildBrowserStateKey(Tenant tenant, OPSessionIdentifier id) {
    return BROWSER_STATE_PREFIX + tenant.identifierValue() + ":" + id.value();
  }

  private String buildUserIndexKey(Tenant tenant, String sub) {
    return IDX_USER_PREFIX + tenant.identifierValue() + ":" + sub;
  }
}
