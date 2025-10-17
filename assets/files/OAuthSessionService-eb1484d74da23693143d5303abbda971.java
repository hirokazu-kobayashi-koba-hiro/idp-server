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

package org.idp.server.adapters.springboot.application.session;

import org.idp.server.core.openid.oauth.OAuthSession;
import org.idp.server.core.openid.oauth.OAuthSessionDelegate;
import org.idp.server.core.openid.oauth.OAuthSessionKey;
import org.idp.server.core.openid.oauth.repository.OAuthSessionRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.springframework.stereotype.Service;

@Service
public class OAuthSessionService implements OAuthSessionDelegate {

  OAuthSessionRepository httpSessionRepository;

  public OAuthSessionService(OAuthSessionRepository httpSessionRepository) {
    this.httpSessionRepository = httpSessionRepository;
  }

  @Override
  public void registerSession(Tenant tenant, OAuthSession oAuthSession) {
    httpSessionRepository.register(tenant, oAuthSession);
  }

  @Override
  public OAuthSession findOrInitialize(OAuthSessionKey oAuthSessionKey) {
    OAuthSession oAuthSession = httpSessionRepository.find(oAuthSessionKey);
    if (oAuthSession.exists()) {
      return oAuthSession;
    }

    return OAuthSession.init(oAuthSessionKey);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    return httpSessionRepository.find(oAuthSessionKey);
  }

  @Override
  public void updateSession(OAuthSession oAuthSession) {
    httpSessionRepository.update(oAuthSession);
  }

  @Override
  public void deleteSession(OAuthSessionKey oAuthSessionKey) {
    httpSessionRepository.delete(oAuthSessionKey);
  }
}
