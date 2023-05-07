package org.idp.server.handler.discovery;

import java.util.Map;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.discovery.JwksResponseCreator;
import org.idp.server.discovery.ServerConfigurationResponseCreator;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.idp.server.handler.discovery.io.JwksRequestStatus;
import org.idp.server.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.handler.discovery.io.ServerConfigurationRequestStatus;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.type.oauth.TokenIssuer;

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
