package org.idp.server.core.oidc.discovery;

import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

public class DefaultDiscoveryProtocolProvider implements ProtocolProvider<DiscoveryProtocol> {

  @Override
  public Class<DiscoveryProtocol> type() {
    return DiscoveryProtocol.class;
  }

  @Override
  public DiscoveryProtocol provide(ApplicationComponentContainer container) {

    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    return new DefaultDiscoveryProtocol(authorizationServerConfigurationQueryRepository);
  }
}
