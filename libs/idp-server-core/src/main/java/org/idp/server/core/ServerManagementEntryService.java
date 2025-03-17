package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantService;

@Transactional
public class ServerManagementEntryService implements ServerManagementApi {

  TenantService tenantService;
  ServerConfigurationHandler serverConfigurationHandler;

  public ServerManagementEntryService(
      TenantService tenantService, ServerConfigurationHandler serverConfigurationHandler) {
    this.tenantService = tenantService;
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  // TODO
  public String register(Tenant tenant, String json) {

    serverConfigurationHandler.register(json);

    tenantService.register(tenant);

    return json;
  }
}
