package org.idp.server.core;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface UserManagementApi {
  void register(Tenant tenant, User user);

  User get(String userId);

  User findBy(Tenant tenant, String email, String providerId);

  void update(User user);

  User findByProvider(String tokenIssuer, String providerId, String providerUserId);

  List<User> find(Tenant tenant, int limit, int offset);

  boolean authenticate(User user, String rawPassword);
}
