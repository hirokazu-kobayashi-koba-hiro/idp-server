package org.idp.server.core;

import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.function.ServerManagementFunction;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantService;

@Transactional
public class ServerManagementService implements ServerManagementFunction {

  TenantService tenantService;
  ServerConfigurationHandler serverConfigurationHandler;

  public ServerManagementService(
      TenantService tenantService, ServerConfigurationHandler serverConfigurationHandler) {
    this.tenantService = tenantService;
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  // TODO
  public String register(Tenant tenant, String json) {

    tenantService.register(tenant);

    serverConfigurationHandler.register(json);
    return json;
  }
}
