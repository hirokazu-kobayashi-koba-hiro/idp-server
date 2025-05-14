package org.idp.server.core.token;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.protocol.ProtocolProvider;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultTokenProtocolProvider implements ProtocolProvider<TokenProtocol> {

  @Override
  public Class<TokenProtocol> type() {
    return TokenProtocol.class;
  }

  @Override
  public TokenProtocol provide(ApplicationComponentContainer container) {

    AuthorizationRequestRepository authorizationRequestRepository =
        container.resolve(AuthorizationRequestRepository.class);
    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    AuthorizationCodeGrantRepository authorizationCodeGrantRepository =
        container.resolve(AuthorizationCodeGrantRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);
    BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository =
        container.resolve(BackchannelAuthenticationRequestRepository.class);
    CibaGrantRepository cibaGrantRepository = container.resolve(CibaGrantRepository.class);
    PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate =
        container.resolve(PasswordCredentialsGrantDelegate.class);

    return new DefaultTokenProtocol(
        authorizationRequestRepository,
        authorizationCodeGrantRepository,
        authorizationGrantedRepository,
        backchannelAuthenticationRequestRepository,
        cibaGrantRepository,
        oAuthTokenRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository,
        passwordCredentialsGrantDelegate);
  }
}
