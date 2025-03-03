package org.idp.server.core.handler.discovery;

import java.util.Map;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.discovery.JwksResponseCreator;
import org.idp.server.core.discovery.ServerConfigurationResponseCreator;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.handler.discovery.io.JwksRequestStatus;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestStatus;
import org.idp.server.core.type.oauth.TokenIssuer;

public class DiscoveryHandler {

  ServerConfigurationRepository serverConfigurationRepository;

  public DiscoveryHandler(ServerConfigurationRepository serverConfigurationRepository) {
    this.serverConfigurationRepository = serverConfigurationRepository;
  }

  public ServerConfigurationRequestResponse getConfiguration(String issuer) {
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(new TokenIssuer(issuer));

    ServerConfigurationResponseCreator serverConfigurationResponseCreator =
        new ServerConfigurationResponseCreator(serverConfiguration);
    Map<String, Object> content = serverConfigurationResponseCreator.create();

    return new ServerConfigurationRequestResponse(ServerConfigurationRequestStatus.OK, content);
  }

  public JwksRequestResponse getJwks(String issuer) {
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(new TokenIssuer(issuer));

    JwksResponseCreator jwksResponseCreator = new JwksResponseCreator(serverConfiguration);
    Map<String, Object> content = jwksResponseCreator.create();

    return new JwksRequestResponse(JwksRequestStatus.OK, content);
  }
}
