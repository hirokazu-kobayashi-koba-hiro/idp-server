package org.idp.server.usecases.control.plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.control.plane.ServerManagementApi;
import org.idp.server.core.multi_tenancy.organization.initial.TenantCreator;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.handler.ServerConfigurationHandler;

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
