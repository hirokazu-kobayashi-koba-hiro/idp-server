package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.OAuthTokenRequestHandler;
import org.idp.server.handler.io.TokenRequest;
import org.idp.server.handler.io.TokenRequestResponse;
import org.idp.server.handler.io.status.TokenRequestStatus;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenErrorResponse;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.type.TokenRequestParameters;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oauth.Error;

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

      return new TokenRequestResponse(TokenRequestStatus.OK, oAuthToken.tokenResponse());
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      Error error = new Error("server_error");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
      return new TokenRequestResponse(TokenRequestStatus.SERVER_ERROR, tokenErrorResponse);
    }
  }
}
