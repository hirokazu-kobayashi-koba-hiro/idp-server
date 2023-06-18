package org.idp.server.handler.configuration;

import org.idp.server.basic.json.JsonParser;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;

public class ClientConfigurationHandler {

  ClientConfigurationRepository clientConfigurationRepository;
  JsonParser jsonParser;

  public ClientConfigurationHandler(ClientConfigurationRepository clientConfigurationRepository) {
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.jsonParser = JsonParser.createWithSnakeCaseStrategy();
  }

  // TODO
  public void register(String json) {
    ClientConfiguration clientConfiguration = jsonParser.read(json, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
  }
}
