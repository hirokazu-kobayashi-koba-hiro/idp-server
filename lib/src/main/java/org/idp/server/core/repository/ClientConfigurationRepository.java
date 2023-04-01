package org.idp.server.core.repository;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.type.ClientId;
import org.idp.server.core.type.TokenIssuer;

public interface ClientConfigurationRepository {
  ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId);
}
