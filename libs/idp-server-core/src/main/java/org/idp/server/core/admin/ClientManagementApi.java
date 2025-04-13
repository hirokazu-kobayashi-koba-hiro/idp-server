package org.idp.server.core.admin;

import org.idp.server.core.configuration.handler.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.configuration.handler.io.ClientConfigurationManagementResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

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
