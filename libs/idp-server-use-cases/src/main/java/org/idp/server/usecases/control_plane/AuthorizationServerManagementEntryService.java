package org.idp.server.usecases.control_plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.control_plane.AuthorizationServerManagementApi;
import org.idp.server.core.multi_tenancy.organization.initial.TenantCreator;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.handler.ServerConfigurationHandler;

@Transaction
public class AuthorizationServerManagementEntryService implements AuthorizationServerManagementApi {

  TenantCommandRepository tenantCommandRepository;
  ServerConfigurationHandler serverConfigurationHandler;

  public AuthorizationServerManagementEntryService(
      TenantCommandRepository tenantCommandRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.serverConfigurationHandler =
        new ServerConfigurationHandler(authorizationServerConfigurationRepository);
  }

  // TODO
  public String register(
      TenantIdentifier adminTenantIdentifier,
      TenantType tenantType,
      ServerDomain serverDomain,
      String serverConfig) {

    TenantCreator tenantCreator = new TenantCreator(tenantType, serverDomain);
    Tenant newTenant = tenantCreator.create();
    tenantCommandRepository.register(newTenant);

    String replacedBody = serverConfig.replaceAll("IDP_ISSUER", newTenant.tokenIssuerValue());

    AuthorizationServerConfiguration authorizationServerConfiguration =
        serverConfigurationHandler.handleRegistration(newTenant, replacedBody);

    return serverConfig;
  }
}
