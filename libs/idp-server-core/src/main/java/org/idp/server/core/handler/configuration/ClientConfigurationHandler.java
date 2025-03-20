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
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.ClientId;

public class ClientConfigurationHandler {

  ClientConfigurationRepository clientConfigurationRepository;
  JsonConverter jsonConverter;

  public ClientConfigurationHandler(ClientConfigurationRepository clientConfigurationRepository) {
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO
  public String handleRegistration(String json) {
    ClientConfiguration clientConfiguration = jsonConverter.read(json, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
    return json;
  }

  public String handleRegistrationFor(String json) {
    // FIXME
    String replacedJson =
        json.replace("${CLIENT_ID}", UUID.randomUUID().toString())
            .replace("${CLIENT_SECRET}", UUID.randomUUID().toString());
    ClientConfiguration clientConfiguration =
        jsonConverter.read(replacedJson, ClientConfiguration.class);
    clientConfigurationRepository.register(clientConfiguration);
    return replacedJson;
  }

  public ClientConfigurationManagementListResponse handleFinding(
      Tenant tenant, int limit, int offset) {

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationRepository.find(tenant, limit, offset);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfigurations);
    return new ClientConfigurationManagementListResponse(
        ClientConfigurationManagementListStatus.OK, content);
  }

  public ClientConfigurationManagementResponse handleGetting(Tenant tenant, ClientId clientId) {

    ClientConfiguration clientConfiguration = clientConfigurationRepository.get(tenant, clientId);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfiguration);
    return new ClientConfigurationManagementResponse(
        ClientConfigurationManagementStatus.OK, content);
  }
}
