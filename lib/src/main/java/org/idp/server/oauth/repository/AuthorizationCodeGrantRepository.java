package org.idp.server.oauth.repository;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.type.oauth.AuthorizationCode;

public interface AuthorizationCodeGrantRepository {
  void register(AuthorizationCodeGrant authorizationCodeGrant);

  AuthorizationCodeGrant find(AuthorizationCode authorizationCode);
}
