package org.idp.sample.user;

import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.TokenIssuer;

public interface UserRepository {
  void register(TokenIssuer tokenIssuer, User user);

  User find(String userId);

  User get(String userId);
}
