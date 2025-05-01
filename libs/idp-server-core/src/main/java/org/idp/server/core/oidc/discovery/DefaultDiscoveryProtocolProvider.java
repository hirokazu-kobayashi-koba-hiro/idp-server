package org.idp.server.core.oidc.discovery;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.protocol.ProtocolProvider;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;

public class DefaultDiscoveryProtocolProvider implements ProtocolProvider<DiscoveryProtocol> {

  @Override
  public Class<DiscoveryProtocol> type() {
    return DiscoveryProtocol.class;
  }

  @Override
  public DiscoveryProtocol provide(ApplicationComponentContainer container) {

    ServerConfigurationRepository serverConfigurationRepository =
        container.resolve(ServerConfigurationRepository.class);
    return new DefaultDiscoveryProtocol(serverConfigurationRepository);
  }
}
