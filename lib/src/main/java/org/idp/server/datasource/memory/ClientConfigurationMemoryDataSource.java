package org.idp.server.datasource.memory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.IdpServerFailedInitializationException;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.basic.resource.ResourceReadable;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.type.ClientId;

/** ClientConfigurationMemoryDataSource */
public class ClientConfigurationMemoryDataSource
    implements ClientConfigurationRepository, ResourceReadable {

  Map<ClientId, ClientConfiguration> map = new HashMap<>();

  public ClientConfigurationMemoryDataSource(List<String> paths) {
    initialize(paths);
  }

  @Override
  public ClientConfiguration get(ClientId clientId) {
    ClientConfiguration clientConfiguration = map.get(clientId);
    if (Objects.isNull(clientConfiguration)) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientId.value()));
    }
    return clientConfiguration;
  }

  void initialize(List<String> paths) {
    try {
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      for (String path : paths) {
        String json = read(path);
        ClientConfiguration clientConfiguration = jsonParser.read(json, ClientConfiguration.class);
        map.put(clientConfiguration.clientId(), clientConfiguration);
      }
    } catch (IOException e) {
      throw new IdpServerFailedInitializationException(e.getMessage(), e);
    }
  }
}
