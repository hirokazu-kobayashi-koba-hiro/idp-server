package org.idp.server.core.token;

import org.idp.server.core.basic.datasource.DataSourceContainer;
import org.idp.server.core.basic.protcol.ProtocolProvider;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultTokenRevocationProtocolProvider
    implements ProtocolProvider<TokenRevocationProtocol> {
  @Override
  public Class<TokenRevocationProtocol> type() {
    return TokenRevocationProtocol.class;
  }

  @Override
  public TokenRevocationProtocol provide(DataSourceContainer container) {

    ServerConfigurationRepository serverConfigurationRepository =
        container.resolve(ServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        container.resolve(ClientConfigurationRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);

    return new DefaultTokenRevocationProtocol(
        oAuthTokenRepository, serverConfigurationRepository, clientConfigurationRepository);
  }
}
