package org.idp.server;

import org.idp.server.handler.configuration.ClientConfigurationHandler;

public class ClientManagementApi {

  ClientConfigurationHandler clientConfigurationHandler;

  public ClientManagementApi(ClientConfigurationHandler clientConfigurationHandler) {
    this.clientConfigurationHandler = clientConfigurationHandler;
  }

  // TODO
  public void register(String json) {
    clientConfigurationHandler.register(json);
  }
}
