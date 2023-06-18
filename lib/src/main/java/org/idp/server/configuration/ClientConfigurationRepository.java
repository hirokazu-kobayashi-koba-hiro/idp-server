package org.idp.server.configuration;

import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public interface ClientConfigurationRepository {

  void register(ClientConfiguration clientConfiguration);

  ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId);
}
