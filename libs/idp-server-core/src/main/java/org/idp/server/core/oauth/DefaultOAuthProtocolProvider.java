package org.idp.server.core.oauth;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.protocol.ProtocolProvider;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultOAuthProtocolProvider implements ProtocolProvider<OAuthProtocol> {

  @Override
  public Class<OAuthProtocol> type() {
    return OAuthProtocol.class;
  }

  @Override
  public OAuthProtocol provide(ApplicationComponentContainer container) {
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
    OAuthSessionDelegate oAuthSessionDelegate = container.resolve(OAuthSessionDelegate.class);
    return new DefaultOAuthProtocol(
        authorizationRequestRepository,
        serverConfigurationRepository,
        clientConfigurationRepository,
        authorizationGrantedRepository,
        authorizationCodeGrantRepository,
        oAuthTokenRepository,
        oAuthSessionDelegate);
  }
}
