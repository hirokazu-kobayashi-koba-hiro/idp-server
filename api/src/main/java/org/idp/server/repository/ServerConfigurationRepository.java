package org.idp.server.repository;

import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.TokenIssuer;

public interface ServerConfigurationRepository {
  ServerConfiguration get(TokenIssuer tokenIssuer);
}
