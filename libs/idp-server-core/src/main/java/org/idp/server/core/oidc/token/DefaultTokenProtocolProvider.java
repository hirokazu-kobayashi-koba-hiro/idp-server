package org.idp.server.core.oidc.token;

import java.util.Map;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.token.plugin.OAuthTokenCreationServiceLoader;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.core.oidc.token.service.OAuthTokenCreationService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

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
    PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate =
        container.resolve(PasswordCredentialsGrantDelegate.class);
    Map<GrantType, OAuthTokenCreationService> extentions =
        OAuthTokenCreationServiceLoader.load(container);

    return new DefaultTokenProtocol(
        authorizationRequestRepository,
        authorizationCodeGrantRepository,
        authorizationGrantedRepository,
        oAuthTokenRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository,
        passwordCredentialsGrantDelegate,
        extentions);
  }
}
