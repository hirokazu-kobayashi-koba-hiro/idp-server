package org.idp.server.control_plane.management.oidc.client;

import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementResponse;
import org.idp.server.core.token.OAuthToken;

public interface ClientManagementApi {

  ClientManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes);

  ClientManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes);

  ClientConfigurationManagementListResponse find(
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
      RequestedClientId requestedClientId,
      RequestAttributes requestAttributes);

  ClientConfigurationManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestedClientId requestedClientId,
      RequestAttributes requestAttributes);
}
