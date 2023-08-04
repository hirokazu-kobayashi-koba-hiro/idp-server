package org.idp.sample.user;

import java.util.Objects;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.TokenIssuer;
import org.springframework.stereotype.Repository;

@Repository
public class UserDataSource implements UserRepository {

  UserMapper mapper;

  public UserDataSource(UserMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void register(TokenIssuer tokenIssuer, User user) {}

  @Override
  public User find(String userId) {
    User user = mapper.select(userId);
    if (Objects.isNull(user)) {
      return new User();
    }
    return user;
  }

  @Override
  public User get(String userId) {
    User user = mapper.select(userId);
    if (Objects.isNull(user)) {
      throw new RuntimeException("not found user");
    }
    return user;
  }
}
