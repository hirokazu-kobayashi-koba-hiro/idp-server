package org.idp.server;

import org.idp.server.api.ServerManagementApi;
import org.idp.server.basic.sql.Transactional;
import org.idp.server.handler.configuration.ServerConfigurationHandler;

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
