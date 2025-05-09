package org.idp.server.adapters.springboot.session.datasource;

import jakarta.servlet.http.HttpSession;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionKey;
import org.idp.server.core.oidc.repository.OAuthHttpSessionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthHttpSessionDataSource implements OAuthHttpSessionRepository {

  HttpSession httpSession;
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthHttpSessionDataSource.class);

  public OAuthHttpSessionDataSource(HttpSession httpSession) {
    this.httpSession = httpSession;
  }

  @Override
  public void register(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.info("registerSession: {}", sessionKey);
    log.info("register sessionId: {}", httpSession.getId());
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    OAuthSession oAuthSession = (OAuthSession) httpSession.getAttribute(sessionKey);
    log.info("find sessionId: {}", httpSession.getId());
    log.info("findSession: {}", sessionKey);
    if (oAuthSession == null) {
      log.info("session not found");
      return new OAuthSession();
    }
    return oAuthSession;
  }

  @Override
  public void update(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.info("update sessionId: {}", httpSession.getId());
    log.info("updateSession: {}", sessionKey);
    httpSession.getId();
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public void delete(OAuthSessionKey oAuthSessionKey) {
    log.info("delete sessionId: {}", httpSession.getId());
    log.info("deleteSession: {}", oAuthSessionKey.key());
    // FIXME every client
    httpSession.invalidate();
  }
}
