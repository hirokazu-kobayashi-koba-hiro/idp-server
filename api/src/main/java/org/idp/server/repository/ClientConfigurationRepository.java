package org.idp.server.repository;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.type.ClientId;

public interface ClientConfigurationRepository {
  ClientConfiguration get(ClientId clientId);
}
