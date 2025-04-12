package org.idp.server.core.token;

import org.idp.server.core.basic.datasource.DataSourceContainer;
import org.idp.server.core.basic.protcol.ProtocolProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultTokenProtocolProvider implements ProtocolProvider<TokenProtocol> {
  @Override
  public Class<TokenProtocol> type() {
    return TokenProtocol.class;
  }

  @Override
  public TokenProtocol provide(DataSourceContainer container) {

    AuthorizationRequestRepository authorizationRequestRepository =
        container.resolve(AuthorizationRequestRepository.class);
    ServerConfigurationRepository serverConfigurationRepository =
        container.resolve(ServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        container.resolve(ClientConfigurationRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    AuthorizationCodeGrantRepository authorizationCodeGrantRepository =
        container.resolve(AuthorizationCodeGrantRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);
    BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository =
        container.resolve(BackchannelAuthenticationRequestRepository.class);
    CibaGrantRepository cibaGrantRepository = container.resolve(CibaGrantRepository.class);

    return new DefaultTokenProtocol(
        authorizationRequestRepository,
        authorizationCodeGrantRepository,
        authorizationGrantedRepository,
        backchannelAuthenticationRequestRepository,
        cibaGrantRepository,
        oAuthTokenRepository,
        serverConfigurationRepository,
        clientConfigurationRepository);
  }
}
