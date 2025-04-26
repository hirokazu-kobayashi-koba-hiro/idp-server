package org.idp.server.core.admin;

import java.util.List;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;

public interface UserManagementApi {
  void register(TenantIdentifier tenantIdentifier, User user);

  User get(TenantIdentifier tenantIdentifier, String userId);

  User findBy(TenantIdentifier tenantIdentifier, String email, String providerId);

  void update(TenantIdentifier tenantIdentifier, User user);

  List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset);
}
