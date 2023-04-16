package org.idp.server.oauth.datasource.memory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.basic.resource.ResourceReadable;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.type.oauth.TokenIssuer;

/** ServerConfigurationMemoryDataSource */
public class ServerConfigurationMemoryDataSource
    implements ServerConfigurationRepository, ResourceReadable {

  Map<TokenIssuer, ServerConfiguration> map = new HashMap<>();

  public ServerConfigurationMemoryDataSource(List<String> paths) {
    initialize(paths);
  }

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
      JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();
      for (String path : paths) {
        String json = read(path);
        ServerConfiguration serverConfiguration = jsonParser.read(json, ServerConfiguration.class);
        map.put(serverConfiguration.issuer(), serverConfiguration);
      }
    } catch (IOException e) {
      throw new IdpServerFailedInitializationException(e.getMessage(), e);
    }
  }
}
