package org.idp.server.core.configuration.handler;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.tenant.Tenant;

public class ServerConfigurationHandler {

  ServerConfigurationRepository serverConfigurationRepository;
  JsonConverter jsonConverter;

  public ServerConfigurationHandler(ServerConfigurationRepository serverConfigurationRepository) {
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO
  public ServerConfiguration handleRegistration(Tenant tenant, String json) {
    ServerConfiguration serverConfiguration = jsonConverter.read(json, ServerConfiguration.class);
    serverConfiguration.setTenantId(UUID.randomUUID().toString());
    serverConfigurationRepository.register(tenant, serverConfiguration);

    return serverConfiguration;
  }
}
