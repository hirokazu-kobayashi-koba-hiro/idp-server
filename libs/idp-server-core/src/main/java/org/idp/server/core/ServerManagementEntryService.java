package org.idp.server.core;

import java.util.UUID;
import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.organization.initial.TenantCreator;
import org.idp.server.core.tenant.*;

@Transactional
public class ServerManagementEntryService implements ServerManagementApi {

  TenantRepository tenantRepository;
  ServerConfigurationHandler serverConfigurationHandler;

  public ServerManagementEntryService(
      TenantRepository tenantRepository, ServerConfigurationHandler serverConfigurationHandler) {
    this.tenantRepository = tenantRepository;
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  // TODO
  public String register(PublicTenantDomain publicTenantDomain, String serverConfig) {

    String newTenantId = UUID.randomUUID().toString();
    String issuer = publicTenantDomain.value() + newTenantId;
    String replacedBody = serverConfig.replaceAll("IDP_ISSUER", issuer);

    ServerConfiguration serverConfiguration =
        serverConfigurationHandler.handleRegistration(replacedBody);

    TenantCreator tenantCreator =
        new TenantCreator(
            new TenantIdentifier(newTenantId),
            new TenantName(newTenantId),
            serverConfiguration.serverIdentifier(),
            serverConfiguration.tokenIssuer());
    Tenant newTenant = tenantCreator.create();

    tenantRepository.register(newTenant);

    return serverConfig;
  }
}
