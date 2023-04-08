package org.idp.server.core.repository;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface ClientConfigurationRepository {
  ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId);
}
