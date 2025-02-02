package org.idp.sample.user;

import org.idp.sample.Tenant;
import org.idp.server.oauth.identity.User;

import java.util.List;

public interface UserRepository {
  void register(Tenant tenant, User user);

  User find(String userId);

  User get(String userId);

  User findBy(Tenant tenant, String email);

  List<User> findList(Tenant tenant, int limit, int offset);
}
