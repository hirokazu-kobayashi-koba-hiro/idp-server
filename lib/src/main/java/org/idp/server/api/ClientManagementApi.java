package org.idp.server.api;

import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.type.oauth.TokenIssuer;

public interface ClientManagementApi {

  String register(String json);

  ClientConfigurationManagementListResponse find(TokenIssuer tokenIssuer, int limit, int offset);
}
