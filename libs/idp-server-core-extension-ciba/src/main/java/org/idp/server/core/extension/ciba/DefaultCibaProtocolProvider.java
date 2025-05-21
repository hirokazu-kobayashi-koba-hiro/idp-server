package org.idp.server.core.extension.ciba;

import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

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
    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);
    UserQueryRepository userQueryRepository = container.resolve(UserQueryRepository.class);

    return new DefaultCibaProtocol(
        backchannelAuthenticationRequestRepository,
        cibaGrantRepository,
        userQueryRepository,
        authorizationGrantedRepository,
        oAuthTokenRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository);
  }
}
