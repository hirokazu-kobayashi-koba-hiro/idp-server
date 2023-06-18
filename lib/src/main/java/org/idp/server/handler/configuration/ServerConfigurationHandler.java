package org.idp.server.handler.configuration;

import org.idp.server.basic.json.JsonParser;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;

public class ServerConfigurationHandler {

  ServerConfigurationRepository serverConfigurationRepository;
  JsonParser jsonParser;

  public ServerConfigurationHandler(ServerConfigurationRepository serverConfigurationRepository) {
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.jsonParser = JsonParser.createWithSnakeCaseStrategy();
  }

  // TODO
  public void register(String json) {
    ServerConfiguration serverConfiguration = jsonParser.read(json, ServerConfiguration.class);
    serverConfigurationRepository.register(serverConfiguration);
  }
}
