package org.idp.server.handler.discovery;

import java.util.Map;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.dyscovery.JwksResponseCreator;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.idp.server.handler.discovery.io.JwksRequestStatus;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.type.oauth.TokenIssuer;

public class DiscoveryHandler {
  JwksResponseCreator jwksResponseCreator = new JwksResponseCreator();
  ServerConfigurationRepository serverConfigurationRepository;

  public DiscoveryHandler(ServerConfigurationRepository serverConfigurationRepository) {
    this.serverConfigurationRepository = serverConfigurationRepository;
  }

  public JwksRequestResponse getJwks(String issuer) {
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(new TokenIssuer(issuer));
    Map<String, Object> content = jwksResponseCreator.create(serverConfiguration);
    return new JwksRequestResponse(JwksRequestStatus.OK, content);
  }
}
