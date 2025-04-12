package org.idp.server.core.token;

import org.idp.server.core.basic.datasource.DataSourceContainer;
import org.idp.server.core.basic.protcol.ProtocolProvider;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultTokenIntrospectionProtocolProvider
    implements ProtocolProvider<TokenIntrospectionProtocol> {
  @Override
  public Class<TokenIntrospectionProtocol> type() {
    return TokenIntrospectionProtocol.class;
  }

  @Override
  public TokenIntrospectionProtocol provide(DataSourceContainer container) {

    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);

    return new DefaultTokenIntrospectionProtocol(oAuthTokenRepository);
  }
}
