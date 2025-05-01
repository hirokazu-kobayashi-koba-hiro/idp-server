package org.idp.server.usecases;

import org.idp.server.core.admin.ClientManagementApi;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.oidc.configuration.handler.ClientConfigurationErrorHandler;
import org.idp.server.core.oidc.configuration.handler.ClientConfigurationHandler;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementResponse;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.basic.type.oauth.RequestedClientId;

@Transaction
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
