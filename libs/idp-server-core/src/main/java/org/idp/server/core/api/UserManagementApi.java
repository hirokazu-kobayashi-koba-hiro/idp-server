package org.idp.server.core.api;

import java.util.List;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;

public interface UserManagementApi {
  void register(Tenant tenant, User user);

  User get(TenantIdentifier tenantIdentifier, String userId);

  User findBy(Tenant tenant, String email, String providerId);

  void update(User user);

  User findByProvider(String tokenIssuer, String providerId, String providerUserId);

  List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset);
}
