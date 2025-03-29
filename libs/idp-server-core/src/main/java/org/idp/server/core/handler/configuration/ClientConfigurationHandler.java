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
import org.idp.server.core.type.oauth.RequestedClientId;

public class ClientConfigurationHandler {

  ClientConfigurationRepository clientConfigurationRepository;
  JsonConverter jsonConverter;

  public ClientConfigurationHandler(ClientConfigurationRepository clientConfigurationRepository) {
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO
  public String handleRegistration(Tenant tenant, String json) {
    ClientConfiguration clientConfiguration = jsonConverter.read(json, ClientConfiguration.class);
    clientConfigurationRepository.register(tenant, clientConfiguration);
    return json;
  }

  public String handleRegistrationFor(Tenant tenant, String json) {
    // FIXME
    String replacedJson =
        json.replace("${ISSUER}", tenant.tokenIssuerValue())
            .replace("${ISSUER_DOMAIN}", tenant.domain().value())
            .replace("${CLIENT_ID}", UUID.randomUUID().toString())
            .replace("${CLIENT_SECRET}", UUID.randomUUID().toString());
    ClientConfiguration clientConfiguration =
        jsonConverter.read(replacedJson, ClientConfiguration.class);

    clientConfigurationRepository.register(tenant, clientConfiguration);
    return replacedJson;
  }

  public String handleUpdating(Tenant tenant, String json) {
    ClientConfiguration clientConfiguration = jsonConverter.read(json, ClientConfiguration.class);

    clientConfigurationRepository.update(tenant, clientConfiguration);

    return json;
  }

  public ClientConfigurationManagementListResponse handleFinding(
      Tenant tenant, int limit, int offset) {

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationRepository.find(tenant, limit, offset);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfigurations);
    return new ClientConfigurationManagementListResponse(
        ClientConfigurationManagementListStatus.OK, content);
  }

  public ClientConfigurationManagementResponse handleGetting(
      Tenant tenant, RequestedClientId requestedClientId) {

    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, requestedClientId);
    Map<String, Object> content = ClientConfigurationResponseCreator.create(clientConfiguration);
    return new ClientConfigurationManagementResponse(
        ClientConfigurationManagementStatus.OK, content);
  }

  public ClientConfigurationManagementResponse handleDeletion(
      Tenant tenant, RequestedClientId requestedClientId) {

    clientConfigurationRepository.delete(tenant, requestedClientId);

    return new ClientConfigurationManagementResponse(
        ClientConfigurationManagementStatus.OK, Map.of());
  }
}
