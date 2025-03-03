package org.idp.server.core.configuration;

import java.util.List;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface ClientConfigurationRepository {

  void register(ClientConfiguration clientConfiguration);

  ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId);

  List<ClientConfiguration> find(TokenIssuer tokenIssuer, int limit, int offset);
}
