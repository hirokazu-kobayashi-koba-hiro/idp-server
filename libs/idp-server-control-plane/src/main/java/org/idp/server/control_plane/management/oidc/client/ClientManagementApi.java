package org.idp.server.control_plane.management.oidc.client;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.oidc.client.io.ClientConfigurationManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface ClientManagementApi {

  ClientConfigurationManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  ClientConfigurationManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  ClientConfigurationManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes);

  ClientConfigurationManagementResponse update(
          TenantIdentifier tenantIdentifier,
          User operator,
          OAuthToken oAuthToken,
          ClientIdentifier clientIdentifier,
          ClientRegistrationRequest request,
          RequestAttributes requestAttributes,
          boolean dryRun);

  ClientConfigurationManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
