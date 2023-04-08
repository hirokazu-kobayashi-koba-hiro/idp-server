package org.idp.server.core.repository;

import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface ServerConfigurationRepository {
  ServerConfiguration get(TokenIssuer tokenIssuer);
}
