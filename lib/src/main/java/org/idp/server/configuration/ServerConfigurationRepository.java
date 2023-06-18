package org.idp.server.configuration;

import org.idp.server.type.oauth.TokenIssuer;

public interface ServerConfigurationRepository {
  void register(ServerConfiguration serverConfiguration);

  ServerConfiguration get(TokenIssuer tokenIssuer);
}
