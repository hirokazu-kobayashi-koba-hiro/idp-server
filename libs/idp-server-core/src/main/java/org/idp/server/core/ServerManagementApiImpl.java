package org.idp.server.core;

import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;

@Transactional
public class ServerManagementApiImpl implements ServerManagementApi {

  ServerConfigurationHandler serverConfigurationHandler;

  public ServerManagementApiImpl(ServerConfigurationHandler serverConfigurationHandler) {
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  // TODO
  public String register(String json) {
    serverConfigurationHandler.register(json);
    return json;
  }
}
