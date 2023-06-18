package org.idp.server.handler.tokenrevocation;

import java.util.Map;
import org.idp.server.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.tokenrevocation.TokenRevocationRequestContext;
import org.idp.server.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.tokenrevocation.validator.TokenRevocationValidator;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenRevocationHandler {
  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  ClientAuthenticatorHandler clientAuthenticatorHandler;

  public TokenRevocationHandler(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
  }

  public TokenRevocationResponse handle(TokenRevocationRequest request) {
    TokenRevocationValidator validator = new TokenRevocationValidator(request.toParameters());
    validator.validate();

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
    AccessTokenValue accessTokenValue = parameters.accessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenValue);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenValue refreshTokenValue = parameters.refreshToken();
      return oAuthTokenRepository.find(tokenIssuer, refreshTokenValue);
    }
  }
}
