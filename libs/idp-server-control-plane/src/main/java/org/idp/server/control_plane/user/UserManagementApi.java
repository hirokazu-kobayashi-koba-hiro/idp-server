package org.idp.server.control_plane.user;

import java.util.List;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface UserManagementApi {
  void register(TenantIdentifier tenantIdentifier, User user);

  User get(TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier);

  User findBy(TenantIdentifier tenantIdentifier, String email, String providerId);

  void update(TenantIdentifier tenantIdentifier, User user);

  List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset);
}
