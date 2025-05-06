package org.idp.server.core.oidc.configuration.handler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationResponseCreator;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementListStatus;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementStatus;

public class ClientConfigurationHandler {

  ClientConfigurationRepository clientConfigurationRepository;
  JsonConverter jsonConverter;

  public ClientConfigurationHandler(ClientConfigurationRepository clientConfigurationRepository) {
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
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
