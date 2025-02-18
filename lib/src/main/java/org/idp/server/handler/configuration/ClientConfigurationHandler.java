package org.idp.server.handler.configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ClientConfigurationResponseCreator;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListStatus;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementStatus;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;

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

  public String register(TokenIssuer tokenIssuer, String json) {
    // FIXME
    String replacedJson =
        json.replace("${ISSUER}", tokenIssuer.value())
            .replace("${CLIENT_ID}", UUID.randomUUID().toString())
            .replace("${CLIENT_SECRET}", UUID.randomUUID().toString());
    ClientConfiguration clientConfiguration =
        jsonConverter.read(replacedJson, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
    return replacedJson;
  }

  public ClientConfigurationManagementListResponse find(
      TokenIssuer tokenIssuer, int limit, int offset) {

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationRepository.find(tokenIssuer, limit, offset);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfigurations);
    return new ClientConfigurationManagementListResponse(
        ClientConfigurationManagementListStatus.OK, content);
  }

  public ClientConfigurationManagementResponse get(TokenIssuer tokenIssuer, ClientId clientId) {

    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfiguration);
    return new ClientConfigurationManagementResponse(
        ClientConfigurationManagementStatus.OK, content);
  }
}
