package org.idp.server.oauth.repository;

import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.TokenIssuer;

public interface ServerConfigurationRepository {
  ServerConfiguration get(TokenIssuer tokenIssuer);
}