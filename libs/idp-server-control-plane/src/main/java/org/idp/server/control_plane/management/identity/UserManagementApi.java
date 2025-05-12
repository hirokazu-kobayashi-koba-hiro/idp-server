package org.idp.server.control_plane.management.identity;

import java.util.List;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.identity.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.io.UserUpdateRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface UserManagementApi {
  UserManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes);

  User get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes);

  void update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes);

  List<User> find(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);
}
