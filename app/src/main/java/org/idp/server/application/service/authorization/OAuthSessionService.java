package org.idp.server.application.service.authorization;

import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionKey;
import org.idp.server.domain.model.authorization.OAuthHttpSessionRepository;
import org.idp.server.domain.model.authorization.OAuthSessionRepository;
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
