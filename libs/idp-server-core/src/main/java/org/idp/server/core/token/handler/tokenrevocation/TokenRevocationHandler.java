package org.idp.server.core.token.handler.tokenrevocation;

import java.util.Map;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.clientauthenticator.ClientAuthenticatorHandler;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.tokenrevocation.TokenRevocationRequestContext;
import org.idp.server.core.token.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.core.token.tokenrevocation.validator.TokenRevocationValidator;

public class TokenRevocationHandler {
  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  ClientAuthenticatorHandler clientAuthenticatorHandler;

  public TokenRevocationHandler(
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
    this.clientAuthenticatorHandler = new ClientAuthenticatorHandler();
  }

  public TokenRevocationResponse handle(TokenRevocationRequest request) {
    TokenRevocationValidator validator = new TokenRevocationValidator(request.toParameters());
    validator.validate();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tenant, request.clientId());
    TokenRevocationRequestContext tokenRevocationRequestContext =
        new TokenRevocationRequestContext(
            request.clientSecretBasic(),
            request.toClientCert(),
            request.toParameters(),
            authorizationServerConfiguration,
            clientConfiguration);
    clientAuthenticatorHandler.authenticate(tokenRevocationRequestContext);

    OAuthToken oAuthToken = find(request);
    if (oAuthToken.exists()) {
      oAuthTokenRepository.delete(tenant, oAuthToken);
    }
    return new TokenRevocationResponse(TokenRevocationRequestStatus.OK, oAuthToken, Map.of());
  }

  // TODO consider, because duplicated method token introspection handler
  OAuthToken find(TokenRevocationRequest request) {
    TokenRevocationRequestParameters parameters = request.toParameters();
    AccessTokenEntity accessTokenEntity = parameters.accessToken();
    Tenant tenant = request.tenant();
    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);
    if (oAuthToken.exists()) {
      return oAuthToken;
    } else {
      RefreshTokenEntity refreshTokenEntity = parameters.refreshToken();
      return oAuthTokenRepository.find(tenant, refreshTokenEntity);
    }
  }
}
