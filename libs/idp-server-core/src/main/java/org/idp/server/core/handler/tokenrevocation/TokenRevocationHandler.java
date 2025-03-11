package org.idp.server.core.handler.tokenrevocation;

import java.util.Map;
import org.idp.server.core.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.tokenrevocation.TokenRevocationRequestContext;
import org.idp.server.core.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.core.tokenrevocation.validator.TokenRevocationValidator;
import org.idp.server.core.type.oauth.AccessTokenEntity;
import org.idp.server.core.type.oauth.RefreshTokenEntity;
import org.idp.server.core.type.oauth.TokenIssuer;

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
            request.toClientCert(),
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
    AccessTokenEntity accessTokenEntity = parameters.accessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenEntity);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenEntity refreshTokenEntity = parameters.refreshToken();
      return oAuthTokenRepository.find(tokenIssuer, refreshTokenEntity);
    }
  }
}
