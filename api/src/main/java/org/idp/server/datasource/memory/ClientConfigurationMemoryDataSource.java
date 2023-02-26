package org.idp.server.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.repository.ClientConfigurationRepository;
import org.idp.server.type.ClientId;

/** ClientConfigurationMemoryDataSource */
public class ClientConfigurationMemoryDataSource implements ClientConfigurationRepository {

  Map<ClientId, ClientConfiguration> map = new HashMap<>();

  public ClientConfigurationMemoryDataSource() {
    map.put(new ClientId(), new ClientConfiguration());
  }

  @Override
  public ClientConfiguration get(ClientId clientId) {
    ClientConfiguration clientConfiguration = map.get(clientId);
    if (Objects.isNull(clientConfiguration)) {
      throw new RuntimeException(String.format("unregistered client (%s)", clientId.value()));
    }
    return clientConfiguration;
  }
}
