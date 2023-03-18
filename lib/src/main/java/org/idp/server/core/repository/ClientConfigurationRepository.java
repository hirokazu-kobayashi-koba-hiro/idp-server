package org.idp.server.core.repository;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.type.ClientId;

public interface ClientConfigurationRepository {
  ClientConfiguration get(ClientId clientId);
}
