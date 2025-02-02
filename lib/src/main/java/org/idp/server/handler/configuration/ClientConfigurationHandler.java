package org.idp.server.handler.configuration;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ClientConfigurationResponseCreator;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListStatus;
import org.idp.server.type.oauth.TokenIssuer;

import java.util.List;
import java.util.Map;

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

  public ClientConfigurationManagementListResponse find(TokenIssuer tokenIssuer, int limit, int offset) {

    List<ClientConfiguration> clientConfigurations = clientConfigurationRepository.find(tokenIssuer, limit, offset);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfigurations);
    return new ClientConfigurationManagementListResponse(ClientConfigurationManagementListStatus.OK, content);
  }
}
