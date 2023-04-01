package org.idp.server.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.type.AuthorizationCode;

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
