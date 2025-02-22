package org.idp.sample.application.service.authorization;

import org.idp.sample.domain.model.authorization.OAuthSessionRepository;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.OAuthSessionKey;
import org.springframework.stereotype.Service;

@Service
public class OAuthSessionService implements OAuthRequestDelegate {

  OAuthSessionRepository oAuthSessionRepository;

  public OAuthSessionService(OAuthSessionRepository oAuthSessionRepository) {
    this.oAuthSessionRepository = oAuthSessionRepository;
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    oAuthSessionRepository.register(oAuthSession);
  }

  @Override
  public OAuthSession findSession(OAuthSessionKey oAuthSessionKey) {
    return oAuthSessionRepository.find(oAuthSessionKey);
  }

  public void updateSession(OAuthSession oAuthSession) {
    oAuthSessionRepository.update(oAuthSession);
  }

  @Override
  public void deleteSession(OAuthSessionKey oAuthSessionKey) {
    oAuthSessionRepository.delete(oAuthSessionKey);
  }
}
