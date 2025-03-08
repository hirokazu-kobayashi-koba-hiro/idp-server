package org.idp.server.domain.model.user;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.domain.model.tenant.Tenant;

public interface UserRepository {
  void register(Tenant tenant, User user);

  User find(String userId);

  User get(String userId);

  User findBy(Tenant tenant, String email, String providerId);

  List<User> findList(Tenant tenant, int limit, int offset);

  void update(User user);
}
