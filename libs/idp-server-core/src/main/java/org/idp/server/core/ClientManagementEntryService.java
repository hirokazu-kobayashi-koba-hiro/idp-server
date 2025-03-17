package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.ClientManagementApi;
import org.idp.server.core.handler.configuration.ClientConfigurationErrorHandler;
import org.idp.server.core.handler.configuration.ClientConfigurationHandler;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.type.oauth.ClientId;

@Transactional
public class ClientManagementEntryService implements ClientManagementApi {

  TenantService tenantService;
  ClientConfigurationHandler clientConfigurationHandler;
  ClientConfigurationErrorHandler errorHandler;

  public ClientManagementEntryService(
      TenantService tenantService, ClientConfigurationHandler clientConfigurationHandler) {
    this.tenantService = tenantService;
    this.clientConfigurationHandler = clientConfigurationHandler;
    this.errorHandler = new ClientConfigurationErrorHandler();
  }

  // TODO
  public String register(String json) {
    Tenant tenant = tenantService.getAdmin();

    return clientConfigurationHandler.register(tenant.tokenIssuer(), json);
  }

  public ClientConfigurationManagementListResponse find(
      TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantService.get(tenantIdentifier);

    return clientConfigurationHandler.find(tenant.tokenIssuer(), limit, offset);
  }

  @Override
  public ClientConfigurationManagementResponse get(
      TenantIdentifier tenantIdentifier, ClientId clientId) {
    try {
      Tenant tenant = tenantService.get(tenantIdentifier);

      return clientConfigurationHandler.get(tenant.tokenIssuer(), clientId);
    } catch (Exception e) {

      return errorHandler.handle(e);
    }
  }
}
