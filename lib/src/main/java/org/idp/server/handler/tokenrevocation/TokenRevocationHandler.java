package org.idp.server.handler.tokenrevocation;

import java.util.Map;
import org.idp.server.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.tokenrevocation.TokenRevocationRequestContext;
import org.idp.server.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.tokenrevocation.validator.TokenRevocationValidator;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.RefreshToken;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenRevocationHandler {
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  TokenRevocationValidator validator;
  ClientAuthenticatorHandler clientAuthenticatorHandler;

  public TokenRevocationHandler(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.validator = new TokenRevocationValidator();
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
  }

  public TokenRevocationResponse handle(TokenRevocationRequest request) {
    validator.validate(request.toParameters());
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, request.clientId());
    TokenRevocationRequestContext tokenRevocationRequestContext =
        new TokenRevocationRequestContext(
            request.clientSecretBasic(),
            request.toParameters(),
            serverConfiguration,
            clientConfiguration);
    clientAuthenticatorHandler.authenticate(tokenRevocationRequestContext);

    OAuthToken oAuthToken = find(request);
    if (oAuthToken.exists()) {
      oAuthTokenRepository.delete(oAuthToken);
    }
    return new TokenRevocationResponse(TokenRevocationRequestStatus.OK, Map.of());
  }

  // TODO consider, because duplicated method token introspection handler
  OAuthToken find(TokenRevocationRequest request) {
    TokenRevocationRequestParameters parameters = request.toParameters();
    AccessToken accessToken = parameters.accessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessToken);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshToken refreshToken = parameters.refreshToken();
      return oAuthTokenRepository.find(tokenIssuer, refreshToken);
    }
  }
}
