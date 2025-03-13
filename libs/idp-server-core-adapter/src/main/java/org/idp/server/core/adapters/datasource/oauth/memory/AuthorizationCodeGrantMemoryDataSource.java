package org.idp.server.core.adapters.datasource.oauth.memory;

import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.type.oauth.AuthorizationCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

  @Override
  public void delete(AuthorizationCodeGrant authorizationCodeGrant) {
    map.remove(authorizationCodeGrant.authorizationCode());
  }
}
