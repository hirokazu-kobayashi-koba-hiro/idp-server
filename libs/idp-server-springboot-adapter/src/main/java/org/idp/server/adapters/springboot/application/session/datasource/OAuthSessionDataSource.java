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

package org.idp.server.adapters.springboot.application.session.datasource;

import jakarta.servlet.http.HttpSession;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.repository.OAuthSessionRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthSessionDataSource implements OAuthSessionRepository {

  HttpSession httpSession;
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthSessionDataSource.class);

  public OAuthSessionDataSource(HttpSession httpSession) {
    this.httpSession = httpSession;
  }

  @Override
  public void register(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.debug("registerSession: {}", sessionKey);
    log.debug("register sessionId: {}", httpSession.getId());
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    OAuthSession oAuthSession = (OAuthSession) httpSession.getAttribute(sessionKey);
    log.debug("find sessionId: {}", httpSession.getId());
    log.debug("findSession: {}", sessionKey);
    if (oAuthSession == null) {
      log.debug("session not found");
      return new OAuthSession();
    }
    return oAuthSession;
  }

  @Override
  public void update(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.debug("update sessionId: {}", httpSession.getId());
    log.debug("updateSession: {}", sessionKey);
    httpSession.getId();
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public void delete(OAuthSessionKey oAuthSessionKey) {
    log.debug("delete sessionId: {}", httpSession.getId());
    log.debug("deleteSession: {}", oAuthSessionKey.key());
    // FIXME every client
    httpSession.invalidate();
  }
}
