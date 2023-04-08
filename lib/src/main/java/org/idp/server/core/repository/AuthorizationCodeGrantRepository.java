package org.idp.server.core.repository;

import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.type.oauth.AuthorizationCode;

public interface AuthorizationCodeGrantRepository {
  void register(AuthorizationCodeGrant authorizationCodeGrant);

  AuthorizationCodeGrant find(AuthorizationCode authorizationCode);
}
