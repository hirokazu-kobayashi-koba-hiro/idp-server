package org.idp.server.control_plane.management.user;

import java.util.List;

import org.idp.server.control_plane.management.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.user.io.UserRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public interface UserManagementApi {
  UserManagementResponse register(TenantIdentifier tenantIdentifier, UserRegistrationRequest request);

  User get(TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier);

  User findBy(TenantIdentifier tenantIdentifier, String email, String providerId);

  void update(TenantIdentifier tenantIdentifier, User user);

  List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset);
}
