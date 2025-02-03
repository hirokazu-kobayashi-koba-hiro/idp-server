package org.idp.server;

import org.idp.server.api.ClientManagementApi;
import org.idp.server.basic.sql.Transactional;
import org.idp.server.handler.configuration.ClientConfigurationHandler;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.type.oauth.TokenIssuer;

@Transactional
public class ClientManagementApiImpl implements ClientManagementApi {

  ClientConfigurationHandler clientConfigurationHandler;

  public ClientManagementApiImpl(ClientConfigurationHandler clientConfigurationHandler) {
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
