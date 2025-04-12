package org.idp.server.core.token;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.token.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultTokenRevocationProtocol implements TokenRevocationProtocol {

  TokenRevocationHandler handler;
  Logger log = Logger.getLogger(DefaultTokenRevocationProtocol.class.getName());

  public DefaultTokenRevocationProtocol(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.handler =
        new TokenRevocationHandler(
            oAuthTokenRepository, serverConfigurationRepository, clientConfigurationRepository);
  }

  public TokenRevocationResponse revoke(TokenRevocationRequest request) {
    try {
      return handler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new TokenRevocationResponse(TokenRevocationRequestStatus.SERVER_ERROR, Map.of());
    }
  }
}
