package org.idp.server.adapters.springboot.application.session;

import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.repository.OAuthHttpSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class OAuthSessionService implements OAuthSessionDelegate {

  OAuthHttpSessionRepository httpSessionRepository;

  public OAuthSessionService(OAuthHttpSessionRepository httpSessionRepository) {
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
