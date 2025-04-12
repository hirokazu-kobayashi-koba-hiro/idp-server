package org.idp.server.core.ciba;

import org.idp.server.core.basic.dependency.ApplicationComponentContainer;
import org.idp.server.core.basic.dependency.protcol.ProtocolProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultCibaProvider implements ProtocolProvider<CibaProtocol> {

  @Override
  public Class<CibaProtocol> type() {
    return CibaProtocol.class;
  }

  @Override
  public CibaProtocol provide(ApplicationComponentContainer container) {

    BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository =
        container.resolve(BackchannelAuthenticationRequestRepository.class);
    CibaGrantRepository cibaGrantRepository = container.resolve(CibaGrantRepository.class);
    ServerConfigurationRepository serverConfigurationRepository =
        container.resolve(ServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        container.resolve(ClientConfigurationRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);

    return new DefaultCibaProtocol(
        backchannelAuthenticationRequestRepository,
        cibaGrantRepository,
        authorizationGrantedRepository,
        oAuthTokenRepository,
        serverConfigurationRepository,
        clientConfigurationRepository);
  }
}
