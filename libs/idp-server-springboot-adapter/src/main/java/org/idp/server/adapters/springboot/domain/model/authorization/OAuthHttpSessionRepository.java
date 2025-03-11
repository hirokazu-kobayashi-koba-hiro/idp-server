package org.idp.server.adapters.springboot.domain.model.authorization;

import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.OAuthSessionKey;

public interface OAuthHttpSessionRepository {

  void register(OAuthSession oAuthSession);

  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  void update(OAuthSession oAuthSession);

  void delete(OAuthSessionKey oAuthSessionKey);
}
