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
import org.idp.server.platform.datasource.session.SessionStore;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OPSessionDataSource
 *
 * <p>Implementation of OPSessionRepository using SessionStore abstraction. Works with both Redis
 * and InMemory backends depending on the SessionStore implementation.
 */
public class OPSessionDataSource implements OPSessionRepository {

  private static final String KEY_PREFIX = "op_session:";
  private static final String BROWSER_STATE_PREFIX = "browser_state:";

  private final SessionStore sessionStore;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  private final LoggerWrapper log = LoggerWrapper.getLogger(OPSessionDataSource.class);

  public OPSessionDataSource(SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public void save(Tenant tenant, OPSession session) {
    String key = buildKey(tenant, session.id());
    String json = jsonConverter.write(session);
    long ttl = session.ttlSeconds();

    sessionStore.set(key, json, ttl);

    // browser_state も保存
    if (session.browserState() != null && session.browserState().exists()) {
      String browserStateKey = buildBrowserStateKey(tenant, session.id());
      sessionStore.set(browserStateKey, session.browserState().value(), ttl);
    }

    log.debug(
        "Saved OP session. id:{}, tenant:{}, ttl:{}",
        session.id().value(),
        tenant.identifierValue(),
        ttl);
  }

  @Override
  public Optional<OPSession> findById(Tenant tenant, OPSessionIdentifier id) {
    String key = buildKey(tenant, id);
    return sessionStore
        .get(key)
        .map(
            json -> {
              OPSession session = jsonConverter.read(json, OPSession.class);
              log.debug("Found OP session. id:{}, tenant:{}", id.value(), tenant.identifierValue());
              return session;
            });
  }

  @Override
  public void delete(Tenant tenant, OPSessionIdentifier id) {
    String key = buildKey(tenant, id);
    String browserStateKey = buildBrowserStateKey(tenant, id);

    sessionStore.delete(key, browserStateKey);
    log.debug("Deleted OP session. id:{}, tenant:{}", id.value(), tenant.identifierValue());
  }

  @Override
  public void updateLastAccessedAt(Tenant tenant, OPSession session) {
    String key = buildKey(tenant, session.id());
    String json = jsonConverter.write(session);
    long ttl = sessionStore.ttl(key);

    sessionStore.set(key, json, ttl > 0 ? ttl : 0);

    log.debug(
        "Updated OP session lastAccessedAt. id:{}, tenant:{}",
        session.id().value(),
        tenant.identifierValue());
  }

  private String buildKey(Tenant tenant, OPSessionIdentifier id) {
    return KEY_PREFIX + tenant.identifierValue() + ":" + id.value();
  }

  private String buildBrowserStateKey(Tenant tenant, OPSessionIdentifier id) {
    return BROWSER_STATE_PREFIX + tenant.identifierValue() + ":" + id.value();
  }
}
