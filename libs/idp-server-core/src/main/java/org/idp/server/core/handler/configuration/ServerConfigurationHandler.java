package org.idp.server.core.handler.configuration;

import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;

public class ServerConfigurationHandler {

  ServerConfigurationRepository serverConfigurationRepository;
  JsonConverter jsonConverter;

  public ServerConfigurationHandler(ServerConfigurationRepository serverConfigurationRepository) {
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO
  public void register(String json) {
    ServerConfiguration serverConfiguration = jsonConverter.read(json, ServerConfiguration.class);
    serverConfigurationRepository.register(serverConfiguration);
  }
}
