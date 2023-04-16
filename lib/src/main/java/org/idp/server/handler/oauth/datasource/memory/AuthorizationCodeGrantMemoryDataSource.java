package org.idp.server.handler.oauth.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.type.oauth.AuthorizationCode;

public class AuthorizationCodeGrantMemoryDataSource implements AuthorizationCodeGrantRepository {

  Map<AuthorizationCode, AuthorizationCodeGrant> map = new HashMap<>();

  @Override
  public void register(AuthorizationCodeGrant authorizationCodeGrant) {
    map.put(authorizationCodeGrant.authorizationCode(), authorizationCodeGrant);
  }

  @Override
  public AuthorizationCodeGrant find(AuthorizationCode authorizationCode) {
    AuthorizationCodeGrant authorizationCodeGrant = map.get(authorizationCode);
    if (Objects.isNull(authorizationCodeGrant)) {
      return new AuthorizationCodeGrant();
    }
    return authorizationCodeGrant;
  }
}
