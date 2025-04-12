package org.idp.server.core.discovery;

import org.idp.server.core.basic.datasource.DataSourceContainer;
import org.idp.server.core.basic.protcol.ProtocolProvider;
import org.idp.server.core.configuration.ServerConfigurationRepository;

public class DefaultJwksProtocolProvider implements ProtocolProvider<JwksProtocol> {
  @Override
  public Class<JwksProtocol> type() {
    return JwksProtocol.class;
  }

  @Override
  public JwksProtocol provide(DataSourceContainer container) {

    ServerConfigurationRepository serverConfigurationRepository =
        container.resolve(ServerConfigurationRepository.class);
    return new DefaultJwksProtocol(serverConfigurationRepository);
  }
}
