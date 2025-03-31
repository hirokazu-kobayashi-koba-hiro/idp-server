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

  public OAuthSessionService(
      OAuthHttpSessionRepository httpSessionRepository) {
    this.httpSessionRepository = httpSessionRepository;
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    httpSessionRepository.register(oAuthSession);
  }

  @Override
  public OAuthSession findSession(OAuthSessionKey oAuthSessionKey) {
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
