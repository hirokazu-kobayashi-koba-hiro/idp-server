package org.idp.server.core.adapters.datasource.configuration.memory;

import org.idp.server.core.adapters.datasource.oauth.memory.IdpServerFailedInitializationException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.resource.ResourceReadable;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.type.oauth.TokenIssuer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** ServerConfigurationMemoryDataSource */
public class ServerConfigurationMemoryDataSource
    implements ServerConfigurationRepository, ResourceReadable {

  Map<TokenIssuer, ServerConfiguration> map = new HashMap<>();

  public ServerConfigurationMemoryDataSource(List<String> paths) {
    initialize(paths);
  }

  @Override
  public void register(ServerConfiguration serverConfiguration) {}

  @Override
  public ServerConfiguration get(TokenIssuer tokenIssuer) {
    ServerConfiguration serverConfiguration = map.get(tokenIssuer);
    if (Objects.isNull(serverConfiguration)) {
      throw new ServerConfigurationNotFoundException(
          String.format("unregistered server configuration (%s)", tokenIssuer.value()));
    }
    return serverConfiguration;
  }

  void initialize(List<String> paths) {
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      for (String path : paths) {
        String json = read(path);
        ServerConfiguration serverConfiguration =
            jsonConverter.read(json, ServerConfiguration.class);
        map.put(serverConfiguration.tokenIssuer(), serverConfiguration);
      }
    } catch (IOException e) {
      throw new IdpServerFailedInitializationException(e.getMessage(), e);
    }
  }
}
