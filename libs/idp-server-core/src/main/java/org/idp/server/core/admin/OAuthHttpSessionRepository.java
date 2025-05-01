package org.idp.server.core.admin;

import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionKey;

public interface OAuthHttpSessionRepository {

  void register(OAuthSession oAuthSession);

  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  void update(OAuthSession oAuthSession);

  void delete(OAuthSessionKey oAuthSessionKey);
}
