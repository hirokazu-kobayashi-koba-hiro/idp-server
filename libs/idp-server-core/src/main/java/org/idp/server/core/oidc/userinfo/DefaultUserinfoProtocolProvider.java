package org.idp.server.core.oidc.userinfo;

import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

public class DefaultUserinfoProtocolProvider implements ProtocolProvider<UserinfoProtocol> {

  @Override
  public Class<UserinfoProtocol> type() {
    return UserinfoProtocol.class;
  }

  @Override
  public UserinfoProtocol provide(ApplicationComponentContainer container) {

    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);

    return new DefaultUserinfoProtocol(
        oAuthTokenRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository);
  }
}
