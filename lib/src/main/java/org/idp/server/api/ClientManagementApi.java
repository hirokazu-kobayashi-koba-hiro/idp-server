package org.idp.server.api;

import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public interface ClientManagementApi {

  String register(String json);

  String register(TokenIssuer tokenIssuer, String json);

  ClientConfigurationManagementListResponse find(TokenIssuer tokenIssuer, int limit, int offset);

  ClientConfigurationManagementResponse get(TokenIssuer tokenIssuer, ClientId clientId);
}
