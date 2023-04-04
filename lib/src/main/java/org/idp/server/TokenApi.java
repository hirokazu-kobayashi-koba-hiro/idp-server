package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.TokenRequestContext;
import org.idp.server.core.oauth.token.OAuthToken;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.core.type.*;
import org.idp.server.handler.token.OAuthTokenRequestHandler;
import org.idp.server.io.TokenRequest;
import org.idp.server.io.TokenRequestResponse;

public class TokenApi {

  OAuthTokenRequestHandler tokenRequestHandler;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  Logger log = Logger.getLogger(TokenApi.class.getName());

  TokenApi(
      OAuthTokenRequestHandler tokenRequestHandler,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.tokenRequestHandler = tokenRequestHandler;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    TokenIssuer tokenIssuer = tokenRequest.toTokenIssuer();
    TokenRequestParameters parameters = tokenRequest.toParameters();
    try {
      ClientSecretBasic clientSecretBasic = tokenRequest.clientSecretBasic();
      ClientId clientId = tokenRequest.clientId();
      ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
      ClientConfiguration clientConfiguration =
          clientConfigurationRepository.get(tokenIssuer, clientId);
      // TODO request validate
      TokenRequestContext tokenRequestContext =
          new TokenRequestContext(
              clientSecretBasic, parameters, serverConfiguration, clientConfiguration);
      OAuthToken oAuthToken = tokenRequestHandler.handle(tokenRequestContext);

      return new TokenRequestResponse();
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new TokenRequestResponse();
    }
  }
}
