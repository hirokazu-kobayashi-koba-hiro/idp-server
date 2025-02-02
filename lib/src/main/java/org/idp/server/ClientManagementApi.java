package org.idp.server;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.handler.configuration.ClientConfigurationHandler;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.type.oauth.TokenIssuer;

import java.util.List;

public class ClientManagementApi {

  ClientConfigurationHandler clientConfigurationHandler;

  public ClientManagementApi(ClientConfigurationHandler clientConfigurationHandler) {
    this.clientConfigurationHandler = clientConfigurationHandler;
  }

  // TODO
  public String register(String json) {
    clientConfigurationHandler.register(json);
    return json;
  }

  public ClientConfigurationManagementListResponse find(TokenIssuer tokenIssuer, int limit, int offset) {

    return clientConfigurationHandler.find(tokenIssuer, limit, offset);
  }
}
