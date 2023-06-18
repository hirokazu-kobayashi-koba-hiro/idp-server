package org.idp.server.handler.configuration.datasource.memory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.basic.resource.ResourceReadable;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.handler.oauth.datasource.memory.IdpServerFailedInitializationException;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

/** ClientConfigurationMemoryDataSource */
public class ClientConfigurationMemoryDataSource
    implements ClientConfigurationRepository, ResourceReadable {

  Map<MultiClientIdentifier, ClientConfiguration> map = new HashMap<>();

  public ClientConfigurationMemoryDataSource(List<String> paths) {
    initialize(paths);
  }

  @Override
  public void register(ClientConfiguration clientConfiguration) {}

  @Override
  public ClientConfiguration get(TokenIssuer tokenIssuer, ClientId clientId) {
    MultiClientIdentifier multiClientIdentifier = new MultiClientIdentifier(tokenIssuer, clientId);
    ClientConfiguration clientConfiguration = map.get(multiClientIdentifier);
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
        TokenIssuer tokenIssuer = clientConfiguration.tokenIssuer();
        ClientId clientId = clientConfiguration.clientId();
        MultiClientIdentifier multiClientIdentifier =
            new MultiClientIdentifier(tokenIssuer, clientId);
        map.put(multiClientIdentifier, clientConfiguration);
      }
    } catch (IOException e) {
      throw new IdpServerFailedInitializationException(e.getMessage(), e);
    }
  }
}
