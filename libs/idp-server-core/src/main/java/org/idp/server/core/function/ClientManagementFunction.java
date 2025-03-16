package org.idp.server.core.function;

import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.ClientId;

public interface ClientManagementFunction {

  String register(TenantIdentifier tenantIdentifier, String json);

  ClientConfigurationManagementListResponse find(
      TenantIdentifier tenantIdentifier, int limit, int offset);

  ClientConfigurationManagementResponse get(TenantIdentifier tenantIdentifier, ClientId clientId);
}
