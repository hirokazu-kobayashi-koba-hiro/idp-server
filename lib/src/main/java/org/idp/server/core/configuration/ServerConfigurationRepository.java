package org.idp.server.core.configuration;

import org.idp.server.core.type.oauth.TokenIssuer;

public interface ServerConfigurationRepository {
  void register(ServerConfiguration serverConfiguration);

  ServerConfiguration get(TokenIssuer tokenIssuer);
}
