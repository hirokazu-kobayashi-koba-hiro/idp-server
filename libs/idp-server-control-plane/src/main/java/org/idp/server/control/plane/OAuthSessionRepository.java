package org.idp.server.control.plane;

import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.OAuthSessionKey;

public interface OAuthSessionRepository {

  void register(OAuthSession oAuthSession);

  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  void update(OAuthSession oAuthSession);

  void delete(OAuthSessionKey oAuthSessionKey);
}
