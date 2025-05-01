package org.idp.server.core.discovery.handler;

import java.util.Map;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.discovery.JwksResponseCreator;
import org.idp.server.core.discovery.ServerConfigurationResponseCreator;
import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.JwksRequestStatus;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestStatus;
import org.idp.server.core.tenant.Tenant;

public class DiscoveryHandler {

  ServerConfigurationRepository serverConfigurationRepository;

  public DiscoveryHandler(ServerConfigurationRepository serverConfigurationRepository) {
    this.serverConfigurationRepository = serverConfigurationRepository;
  }

  public ServerConfigurationRequestResponse getConfiguration(Tenant tenant) {
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tenant);

    ServerConfigurationResponseCreator serverConfigurationResponseCreator =
        new ServerConfigurationResponseCreator(serverConfiguration);
    Map<String, Object> content = serverConfigurationResponseCreator.create();

    return new ServerConfigurationRequestResponse(ServerConfigurationRequestStatus.OK, content);
  }

  public JwksRequestResponse getJwks(Tenant tenant) {
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tenant);

    JwksResponseCreator jwksResponseCreator = new JwksResponseCreator(serverConfiguration);
    Map<String, Object> content = jwksResponseCreator.create();

    return new JwksRequestResponse(JwksRequestStatus.OK, content);
  }
}
