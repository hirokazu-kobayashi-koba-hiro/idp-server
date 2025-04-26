package org.idp.server.core.identity;

import java.util.List;
import org.idp.server.core.tenant.Tenant;

public interface UserRepository {
  void register(Tenant tenant, User user);

  User get(Tenant tenant, String userId);

  User findByEmail(Tenant tenant, String hint, String providerId);

  User findByPhone(Tenant tenant, String hint, String providerId);

  List<User> findList(Tenant tenant, int limit, int offset);

  void update(Tenant tenant, User user);

  User findByProvider(Tenant tenant, String providerId, String providerUserId);
}
