package org.idp.server.core.handler.configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ClientConfigurationResponseCreator;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListStatus;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementStatus;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

public class ClientConfigurationHandler {

  ClientConfigurationRepository clientConfigurationRepository;
  JsonConverter jsonConverter;

  public ClientConfigurationHandler(ClientConfigurationRepository clientConfigurationRepository) {
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO
  public void handleRegistration(String json) {
    ClientConfiguration clientConfiguration = jsonConverter.read(json, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
  }

  public String handleRegistration(TokenIssuer tokenIssuer, String json) {
    // FIXME
    String replacedJson =
        json.replace("${ISSUER}", tokenIssuer.value())
            .replace("${ISSUER_DOMAIN}", tokenIssuer.extractDomain())
            .replace("${CLIENT_ID}", UUID.randomUUID().toString())
            .replace("${CLIENT_SECRET}", UUID.randomUUID().toString());
    ClientConfiguration clientConfiguration =
        jsonConverter.read(replacedJson, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
    return replacedJson;
  }

  public ClientConfigurationManagementListResponse handleFinding(
      TokenIssuer tokenIssuer, int limit, int offset) {

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationRepository.find(tokenIssuer, limit, offset);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfigurations);
    return new ClientConfigurationManagementListResponse(
        ClientConfigurationManagementListStatus.OK, content);
  }

  public ClientConfigurationManagementResponse handleGetting(TokenIssuer tokenIssuer, ClientId clientId) {

    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, clientId);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfiguration);
    return new ClientConfigurationManagementResponse(
        ClientConfigurationManagementStatus.OK, content);
  }
}
