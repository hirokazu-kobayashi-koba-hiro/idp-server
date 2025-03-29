package org.idp.server.core;

import org.idp.server.core.api.ClientManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.configuration.ClientConfigurationErrorHandler;
import org.idp.server.core.handler.configuration.ClientConfigurationHandler;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.oauth.RequestedClientId;

@Transactional
public class ClientManagementEntryService implements ClientManagementApi {

  TenantRepository tenantRepository;
  ClientConfigurationHandler clientConfigurationHandler;
  ClientConfigurationErrorHandler errorHandler;

  public ClientManagementEntryService(
      TenantRepository tenantRepository, ClientConfigurationHandler clientConfigurationHandler) {
    this.tenantRepository = tenantRepository;
    this.clientConfigurationHandler = clientConfigurationHandler;
    this.errorHandler = new ClientConfigurationErrorHandler();
  }

  // TODO
  public String register(String json) {

    Tenant tenant = tenantRepository.getAdmin();
    return clientConfigurationHandler.handleRegistration(tenant, json);
  }

  public String register(TenantIdentifier tenantIdentifier, String body) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    return clientConfigurationHandler.handleRegistrationFor(tenant, body);
  }

  public String update(TenantIdentifier tenantIdentifier, String body) {

    Tenant tenant = tenantRepository.get(tenantIdentifier);
    return clientConfigurationHandler.handleUpdating(tenant, body);
  }

  public ClientConfigurationManagementListResponse find(
      TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return clientConfigurationHandler.handleFinding(tenant, limit, offset);
  }

  public ClientConfigurationManagementResponse get(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId) {
    try {
      Tenant tenant = tenantRepository.get(tenantIdentifier);

      return clientConfigurationHandler.handleGetting(tenant, requestedClientId);
    } catch (Exception e) {

      return errorHandler.handle(e);
    }
  }

  public ClientConfigurationManagementResponse delete(
      TenantIdentifier tenantIdentifier, RequestedClientId requestedClientId) {
    try {
      Tenant tenant = tenantRepository.get(tenantIdentifier);

      return clientConfigurationHandler.handleDeletion(tenant, requestedClientId);
    } catch (Exception e) {

      return errorHandler.handle(e);
    }
  }
}
