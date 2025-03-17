package org.idp.server.core.api;

import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.ClientId;

public interface ClientManagementApi {

  String register(String json);

  String register(TenantIdentifier tenantIdentifier, String body);

  ClientConfigurationManagementListResponse find(
      TenantIdentifier tenantIdentifier, int limit, int offset);

  ClientConfigurationManagementResponse get(TenantIdentifier tenantIdentifier, ClientId clientId);

}
