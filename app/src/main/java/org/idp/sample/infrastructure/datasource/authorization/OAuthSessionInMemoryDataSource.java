package org.idp.sample.infrastructure.datasource.authorization;

import java.util.HashMap;
import java.util.Map;
import org.idp.sample.domain.model.authorization.OAuthSessionRepository;
import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.OAuthSessionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthSessionInMemoryDataSource implements OAuthSessionRepository {

  Map<String, OAuthSession> map = new HashMap<>();
  Logger log = LoggerFactory.getLogger(OAuthSessionInMemoryDataSource.class);

  @Override
  public void register(OAuthSession oAuthSession) {
    if (map.size() > 10) {
      map.clear();
    }
    String sessionKey = oAuthSession.sessionKeyValue();
    log.info("registerSession: {}", sessionKey);
    map.put(sessionKey, oAuthSession);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    OAuthSession oAuthSession = map.get(sessionKey);
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
    log.info("updateSession: {}", sessionKey);
    map.put(sessionKey, oAuthSession);
  }

  @Override
  public void delete(OAuthSessionKey oAuthSessionKey) {
    log.info("deleteSession: {}", oAuthSessionKey.key());
    map.remove(oAuthSessionKey.key());
  }
}
