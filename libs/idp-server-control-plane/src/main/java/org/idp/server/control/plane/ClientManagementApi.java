package org.idp.server.control.plane;

import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementResponse;

public interface ClientManagementApi {

  String register(TenantIdentifier tenantIdentifier, String body);

  String update(TenantIdentifier tenantIdentifier, String body);

  ClientConfigurationManagementListResponse find(
      TenantIdentifier tenantIdentifier, int limit, int offset);

  ClientConfigurationManagementResponse get(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId);

  ClientConfigurationManagementResponse delete(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId);
}
