package org.idp.server.usecases;

import org.idp.server.core.admin.ServerManagementApi;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.handler.ServerConfigurationHandler;
import org.idp.server.core.organization.initial.TenantCreator;
import org.idp.server.core.tenant.*;

@Transaction
public class ServerManagementEntryService implements ServerManagementApi {

  TenantRepository tenantRepository;
  ServerConfigurationHandler serverConfigurationHandler;

  public ServerManagementEntryService(
      TenantRepository tenantRepository,
      ServerConfigurationRepository serverConfigurationRepository) {
    this.tenantRepository = tenantRepository;
    this.serverConfigurationHandler = new ServerConfigurationHandler(serverConfigurationRepository);
  }

  // TODO
  public String register(
      TenantIdentifier adminTenantIdentifier,
      TenantType tenantType,
      ServerDomain serverDomain,
      String serverConfig) {

    TenantCreator tenantCreator = new TenantCreator(tenantType, serverDomain);
    Tenant newTenant = tenantCreator.create();
    tenantRepository.register(newTenant);

    String replacedBody = serverConfig.replaceAll("IDP_ISSUER", newTenant.tokenIssuerValue());

    ServerConfiguration serverConfiguration =
        serverConfigurationHandler.handleRegistration(newTenant, replacedBody);

    return serverConfig;
  }
}
