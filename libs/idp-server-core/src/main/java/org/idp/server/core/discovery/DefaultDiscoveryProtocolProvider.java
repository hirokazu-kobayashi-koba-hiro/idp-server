package org.idp.server.core.discovery;

import org.idp.server.core.basic.datasource.DataSourceContainer;
import org.idp.server.core.basic.protcol.ProtocolProvider;
import org.idp.server.core.configuration.ServerConfigurationRepository;

public class DefaultDiscoveryProtocolProvider implements ProtocolProvider<DiscoveryProtocol> {
  @Override
  public Class<DiscoveryProtocol> type() {
    return DiscoveryProtocol.class;
  }

  @Override
  public DiscoveryProtocol provide(DataSourceContainer container) {

    ServerConfigurationRepository serverConfigurationRepository =
        container.resolve(ServerConfigurationRepository.class);
    return new DefaultDiscoveryProtocol(serverConfigurationRepository);
  }
}
