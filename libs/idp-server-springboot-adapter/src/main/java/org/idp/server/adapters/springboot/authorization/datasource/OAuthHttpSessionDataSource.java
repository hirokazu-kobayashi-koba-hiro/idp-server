package org.idp.server.adapters.springboot.authorization.datasource;

import jakarta.servlet.http.HttpSession;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionKey;
import org.idp.server.core.admin.OAuthHttpSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthHttpSessionDataSource implements OAuthHttpSessionRepository {

  HttpSession httpSession;
  Logger log = LoggerFactory.getLogger(OAuthHttpSessionDataSource.class);

  public OAuthHttpSessionDataSource(HttpSession httpSession) {
    this.httpSession = httpSession;
  }

  @Override
  public void register(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.info("registerSession: {}", sessionKey);
    log.info("sessionId: {}", httpSession.getId());
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    OAuthSession oAuthSession = (OAuthSession) httpSession.getAttribute(sessionKey);
    log.info("sessionId: {}", httpSession.getId());
    log.info("findSession: {}", sessionKey);
    if (oAuthSession == null) {
      log.info("sessionId not found");
      return new OAuthSession();
    }
    return oAuthSession;
  }

  @Override
  public void update(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.info("sessionId: {}", httpSession.getId());
    log.info("updateSession: {}", sessionKey);
    httpSession.getId();
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public void delete(OAuthSessionKey oAuthSessionKey) {
    log.info("sessionId: {}", httpSession.getId());
    log.info("deleteSession: {}", oAuthSessionKey.key());
    // FIXME every client
    httpSession.invalidate();
  }
}
