package org.idp.server.core.oidc.discovery;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.protocol.ProtocolProvider;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;

public class DefaultDiscoveryProtocolProvider implements ProtocolProvider<DiscoveryProtocol> {

  @Override
  public Class<DiscoveryProtocol> type() {
    return DiscoveryProtocol.class;
  }

  @Override
  public DiscoveryProtocol provide(ApplicationComponentContainer container) {

    AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository =
        container.resolve(AuthorizationServerConfigurationRepository.class);
    return new DefaultDiscoveryProtocol(authorizationServerConfigurationRepository);
  }
}
