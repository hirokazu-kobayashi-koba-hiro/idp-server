package org.idp.server;

import org.idp.server.api.ClientManagementApi;
import org.idp.server.basic.sql.Transactional;
import org.idp.server.handler.configuration.ClientConfigurationErrorHandler;
import org.idp.server.handler.configuration.ClientConfigurationHandler;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

@Transactional
public class ClientManagementApiImpl implements ClientManagementApi {

  ClientConfigurationHandler clientConfigurationHandler;
  ClientConfigurationErrorHandler errorHandler;

  public ClientManagementApiImpl(ClientConfigurationHandler clientConfigurationHandler) {
    this.clientConfigurationHandler = clientConfigurationHandler;
    this.errorHandler = new ClientConfigurationErrorHandler();
  }

  // TODO
  public String register(String json) {
    clientConfigurationHandler.register(json);
    return json;
  }

  public ClientConfigurationManagementListResponse find(TokenIssuer tokenIssuer, int limit, int offset) {

    return clientConfigurationHandler.find(tokenIssuer, limit, offset);
  }

  @Override
  public ClientConfigurationManagementResponse get(TokenIssuer tokenIssuer, ClientId clientId) {
    try {

      return clientConfigurationHandler.get(tokenIssuer, clientId);
    } catch (Exception e) {
      
      return errorHandler.handle(e);
    }
  }
}
