package org.idp.server.control_plane.management.authentication;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.io.UserUpdateRequest;
import org.idp.server.core.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface AuthenticationConfigurationManagementApi {

  AuthenticationConfigManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRegistrationRequest request,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse find(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);
}
