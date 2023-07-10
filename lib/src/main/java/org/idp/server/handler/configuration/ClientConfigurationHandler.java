package org.idp.server.handler.configuration;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;

public class ClientConfigurationHandler {

  ClientConfigurationRepository clientConfigurationRepository;
  JsonConverter jsonConverter;

  public ClientConfigurationHandler(ClientConfigurationRepository clientConfigurationRepository) {
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO
  public void register(String json) {
    ClientConfiguration clientConfiguration = jsonConverter.read(json, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
  }
}
