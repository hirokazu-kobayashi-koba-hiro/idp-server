/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.session;

import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.repository.OAuthSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class OAuthSessionService implements OAuthSessionDelegate {

  OAuthSessionRepository httpSessionRepository;

  public OAuthSessionService(OAuthSessionRepository httpSessionRepository) {
    this.httpSessionRepository = httpSessionRepository;
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    httpSessionRepository.register(oAuthSession);
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
