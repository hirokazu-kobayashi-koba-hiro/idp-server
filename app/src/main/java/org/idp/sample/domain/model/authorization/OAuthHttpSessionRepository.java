package org.idp.sample.domain.model.authorization;

import org.idp.server.oauth.OAuthSession;
import org.idp.server.oauth.OAuthSessionKey;

public interface OAuthHttpSessionRepository {

  void register(OAuthSession oAuthSession);

  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  void update(OAuthSession oAuthSession);

  void delete(OAuthSessionKey oAuthSessionKey);
}
