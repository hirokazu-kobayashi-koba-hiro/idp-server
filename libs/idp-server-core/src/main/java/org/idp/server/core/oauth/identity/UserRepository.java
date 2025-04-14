package org.idp.server.core.oauth.identity;

import java.util.List;
import org.idp.server.core.tenant.Tenant;

public interface UserRepository {
  void register(Tenant tenant, User user);

  User get(Tenant tenant, String userId);

  User findBy(Tenant tenant, String email, String providerId);

  List<User> findList(Tenant tenant, int limit, int offset);

  void update(Tenant tenant, User user);

  User findByProvider(Tenant tenant, String providerId, String providerUserId);
}
