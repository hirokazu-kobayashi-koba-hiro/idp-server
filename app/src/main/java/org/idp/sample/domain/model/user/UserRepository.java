package org.idp.sample.domain.model.user;

import java.util.List;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.server.oauth.identity.User;

public interface UserRepository {
  void register(Tenant tenant, User user);

  User find(String userId);

  User get(String userId);

  User findBy(Tenant tenant, String email);

  List<User> findList(Tenant tenant, int limit, int offset);
}
