package org.idp.server.adapters.springboot.authorization;

import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionKey;
import org.idp.server.core.admin.OAuthHttpSessionRepository;
import org.idp.server.core.admin.OAuthSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class OAuthSessionService implements OAuthRequestDelegate {

  OAuthHttpSessionRepository httpSessionRepository;
  OAuthSessionRepository sessionRepository;

  public OAuthSessionService(
      OAuthHttpSessionRepository httpSessionRepository, OAuthSessionRepository sessionRepository) {
    this.httpSessionRepository = httpSessionRepository;
    this.sessionRepository = sessionRepository;
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    httpSessionRepository.register(oAuthSession);
    sessionRepository.register(oAuthSession);
  }

  @Override
  public OAuthSession findSession(OAuthSessionKey oAuthSessionKey) {
    OAuthSession oAuthSession = httpSessionRepository.find(oAuthSessionKey);

    if (oAuthSession.exists()) {
      return oAuthSession;
    }

    return sessionRepository.find(oAuthSessionKey);
  }

  @Override
  public void updateSession(OAuthSession oAuthSession) {
    httpSessionRepository.update(oAuthSession);
    sessionRepository.update(oAuthSession);
  }

  @Override
  public void deleteSession(OAuthSessionKey oAuthSessionKey) {
    httpSessionRepository.delete(oAuthSessionKey);
    sessionRepository.delete(oAuthSessionKey);
  }
}
