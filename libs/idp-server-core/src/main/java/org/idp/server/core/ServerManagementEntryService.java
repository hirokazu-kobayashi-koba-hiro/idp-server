package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantRepository;

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
  public String register(Tenant tenant, String json) {

    serverConfigurationHandler.handleRegistration(json);

    tenantRepository.register(tenant);

    return json;
  }
}
