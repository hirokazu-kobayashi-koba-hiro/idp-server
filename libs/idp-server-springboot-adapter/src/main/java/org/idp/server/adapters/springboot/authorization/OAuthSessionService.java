package org.idp.server.adapters.springboot.authorization;

import org.idp.server.core.admin.OAuthHttpSessionRepository;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionDelegate;
import org.idp.server.core.oauth.OAuthSessionKey;
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
