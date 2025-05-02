package org.idp.server.core.ciba;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.protocol.ProtocolProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.oidc.configuration.ClientConfigurationRepository;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultCibaProtocolProvider implements ProtocolProvider<CibaProtocol> {

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
    UserRepository userRepository = container.resolve(UserRepository.class);

    return new DefaultCibaProtocol(
        backchannelAuthenticationRequestRepository,
        cibaGrantRepository,
        userRepository,
        authorizationGrantedRepository,
        oAuthTokenRepository,
        serverConfigurationRepository,
        clientConfigurationRepository);
  }
}
