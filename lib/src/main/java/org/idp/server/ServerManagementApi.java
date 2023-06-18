package org.idp.server;

import org.idp.server.handler.configuration.ServerConfigurationHandler;

public class ServerManagementApi {

  ServerConfigurationHandler serverConfigurationHandler;

  public ServerManagementApi(ServerConfigurationHandler serverConfigurationHandler) {
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  // TODO
  public void register(String json) {
    serverConfigurationHandler.register(json);
  }
}
