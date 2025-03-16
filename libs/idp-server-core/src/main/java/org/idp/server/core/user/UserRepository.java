package org.idp.server.core.user;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface UserRepository {
  void register(Tenant tenant, User user);

  User get(String userId);

  User findBy(Tenant tenant, String email, String providerId);

  List<User> findList(Tenant tenant, int limit, int offset);

  void update(User user);

  User findByProvider(String tokenIssuer, String providerId, String providerUserId);
}
