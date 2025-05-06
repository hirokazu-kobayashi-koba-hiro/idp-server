package org.idp.server.core.oidc.userinfo;

import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.protocol.ProtocolProvider;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultUserinfoProtocolProvider implements ProtocolProvider<UserinfoProtocol> {

  @Override
  public Class<UserinfoProtocol> type() {
    return UserinfoProtocol.class;
  }

  @Override
  public UserinfoProtocol provide(ApplicationComponentContainer container) {

    AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository =
        container.resolve(AuthorizationServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        container.resolve(ClientConfigurationRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);

    return new DefaultUserinfoProtocol(
        oAuthTokenRepository,
        authorizationServerConfigurationRepository,
        clientConfigurationRepository);
  }
}
