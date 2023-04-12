package org.idp.server.repository;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

public interface ClientConfigurationRepository {
  ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId);
}
