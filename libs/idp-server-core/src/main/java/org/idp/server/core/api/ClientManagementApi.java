package org.idp.server.core.api;

import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface ClientManagementApi {

  String register(String json);

  String register(TokenIssuer tokenIssuer, String json);

  ClientConfigurationManagementListResponse find(TokenIssuer tokenIssuer, int limit, int offset);

  ClientConfigurationManagementResponse get(TokenIssuer tokenIssuer, ClientId clientId);
}
